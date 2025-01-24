
#include <iostream>
#include <thread>
#include <queue>
#include <mutex>
#include <condition_variable>
#include <atomic>
#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"
#include "../include/KeyboardInput.h"
#include "../include/Frame.h"

std::atomic<bool> running(true);
std::queue<Frame> userToServerQueue;
std::mutex userQueueMutex;
std::condition_variable userQueueCondition;
std::atomic<bool> disconnectReceived(false);

void socketReader(ConnectionHandler &connectionHandler, StompProtocol &protocol, std::atomic<bool> &running, std::atomic<bool> &disconnectReceived);

int main(int argc, char *argv[])
{
	std::atomic<bool> running(true);
	std::atomic<bool> disconnectReceived(false);

	std::cout << "Waiting for login. Use: login <host:port> <username> <password>" << std::endl;
	std::string input;
	std::getline(std::cin, input);

	std::istringstream iss(input);
	std::string command;
	iss >> command;

	if (command == "login")
	{
		std::string hostPort, username, password;
		iss >> hostPort >> username >> password;

		std::string host = hostPort.substr(0, hostPort.find(':'));
		short port = std::stoi(hostPort.substr(hostPort.find(':') + 1));

		ConnectionHandler connectionHandler(host, port);
		if (!connectionHandler.connect())
		{
			std::cerr << "[ERROR] Cannot connect to " << host << ":" << port << std::endl;
			return 1;
		}

		StompProtocol protocol;

		protocol.setUsername(username);
		protocol.setReciptCounter(0);
		protocol.setNextSubscriptionId(0);
		protocol.setLastReceiptId(-1);
		protocol.setActiveConnectionHandler(&connectionHandler);

		Frame connectFrame = protocol.createConnectFrame("stomp.cs.bgu.ac.il", username, password);
		if (!connectionHandler.sendFrameAscii(connectFrame.toString(), '\0'))
		{
			std::cerr << "[ERROR] Failed to send CONNECT frame." << std::endl;
			return 1;
		}

		std::string serverResponse;
		if (!connectionHandler.getFrameAscii(serverResponse, '\0'))
		{
			std::cerr << "[ERROR] Failed to receive server response." << std::endl;
			return 1;
		}
		std::cout << "[SERVER MESSAGE] " << serverResponse << std::endl;

		if (serverResponse.find("CONNECTED") == 0)
		{
			std::cout << "[INFO] Login successful!" << std::endl;
		}
		else
		{
			std::cerr << "[ERROR] Login failed: " << serverResponse << std::endl;
			return 1;
		}

		// Create and start threads
		KeyboardInput keyboardInput(std::ref(protocol), running, disconnectReceived);
		std::thread inputThread(&KeyboardInput::start, &keyboardInput);
		std::thread ioThread(socketReader, std::ref(connectionHandler), std::ref(protocol), std::ref(running), std::ref(disconnectReceived));

		// Wait for threads to finish
		inputThread.join();
		ioThread.join();
	}
	else
	{
		std::cerr << "[ERROR] Invalid command. Expected: login <host:port> <username> <password>" << std::endl;
		return 1;
	}

	std::cout << "[INFO] Client exited." << std::endl;
	return 0;
}
void socketReader(ConnectionHandler &connectionHandler, StompProtocol &protocol, std::atomic<bool> &running, std::atomic<bool> &disconnectReceived)
{
	while (running.load())
	{
		try
		{
			std::string rawFrame;
			if (!connectionHandler.getFrameAscii(rawFrame, '\0'))
			{
				if (running.load())
				{
					throw std::runtime_error("Connection lost while reading from the server.");
				}
				break;
			}

			Frame frame = Frame::parseFrame(rawFrame);
			protocol.processFrame(frame, disconnectReceived);

			if (disconnectReceived.load())
			{
				running.store(false);
				break;
			}
		}
		catch (const std::exception &e)
		{
			if (running.load())
				std::cerr << "[ERROR] socketReader: Exception caught: " << e.what() << std::endl;
			running.store(false);
			break;
		}

		std::this_thread::sleep_for(std::chrono::milliseconds(100));
	}
	std::cout << "[INFO] Socket reader shutting down gracefully." << std::endl;
}
