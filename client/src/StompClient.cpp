
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

void socketReader(ConnectionHandler &connectionHandler, StompProtocol &protocol, std::atomic<bool> &running, std::atomic<bool> &disconnectReceived);

std::mutex disconnectMutex;

int main(int argc, char *argv[])
{
	std::atomic<bool> running(true);
	std::atomic<bool> disconnectReceived(false);

	StompProtocol protocol;
	ConnectionHandler *connectionHandler = nullptr;
	KeyboardInput keyboardInput(std::ref(protocol), running, disconnectReceived);

	std::cout << "Waiting for login. Use: login <host:port> <username> <password>" << std::endl;
	while (running.load())
	{

		try
		{
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

				connectionHandler = new ConnectionHandler(host, port);
				if (!(*connectionHandler).connect())
				{
					std::cerr << "[ERROR] Cannot connect to " << host << ":" << port << std::endl;
					continue;
				}

				protocol.setUsername(username);
				protocol.setReciptCounter(0);
				protocol.setNextSubscriptionId(-1);
				protocol.setLastReceiptId(-1);
				protocol.setActiveConnectionHandler(std::ref(connectionHandler));

				Frame connectFrame = protocol.createConnectFrame("stomp.cs.bgu.ac.il", username, password);
				if (!(*connectionHandler).sendFrameAscii(connectFrame.toString(), '\0'))
				{
					std::cerr << "[ERROR] Failed to send CONNECT frame." << std::endl;
					continue;
				}

				std::string serverResponse;
				if (!(*connectionHandler).getFrameAscii(serverResponse, '\0'))
				{
					std::cerr << "[ERROR] Failed to receive server response." << std::endl;
					continue;
				}
				std::cout << "[SERVER MESSAGE] " << serverResponse << std::endl;

				if (serverResponse.find("CONNECTED") == 0)
				{
					std::cout << "[INFO] Login successful!" << std::endl;
				}
				else
				{
					std::cerr << "[ERROR] Login failed: " << serverResponse << std::endl;
					continue;
				}

				// Create and start threads
				std::thread ioThread(socketReader, std::ref(*connectionHandler), std::ref(protocol), std::ref(running), std::ref(disconnectReceived));
				keyboardInput.start();
				ioThread.join();
			}
			else
			{
				std::cerr << "[ERROR] Invalid command. Expected: login <host:port> <username> <password>" << std::endl;
				continue;
			}
		}
		catch (const std::exception &e)
		{
			std::cerr << "[ERROR] Exception caught: " << e.what() << std::endl;
		}
		disconnectReceived.store(false);
	}

	std::cout << "[INFO] Client Stoped." << std::endl;
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
				std::cout << "[INFO] Disconnect received. Shutting down." << std::endl;
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
