

#include "../include/keyboardInput.h"
#include <fstream>
#include "../include/json.hpp"
#include "../include/StompProtocol.h"

using json = nlohmann::json;
KeyboardInput::KeyboardInput(StompProtocol &protocol, std::queue<Frame> &queue, std::mutex &mutex,
                             std::atomic<bool> &running, std::condition_variable &condition)
    : protocol(protocol), userToServerQueue(queue), queueMutex(mutex), running(running), queueCondition(condition) {}

void KeyboardInput::start()
{
    std::string command;
    while (running.load())
    {
        try
        {
            std::cout << "Enter command: ";
            std::getline(std::cin, command);

            if (command.empty())
                continue;

            std::istringstream iss(command);
            std::string cmd;
            iss >> cmd;

            if (cmd == "login")
            {
                handleLogin(iss);
            }
            else if (cmd == "logout")
            {
                handleLogout();
            }
            else if (cmd == "join")
            {
                handleJoin(iss);
            }
            else if (cmd == "exit")
            {
                handleExit(iss);
            }
            else if (cmd == "report")
            {
                handleReport(iss);
            }
            else
            {
                std::cerr << "[ERROR] Unknown command: " << cmd << std::endl;
            }
        }
        catch (const std::exception &e)
        {
            std::cerr << "[ERROR] KeyboardInput: " << e.what() << std::endl;
        }
    }
}

void KeyboardInput::handleLogin(std::istringstream &iss)
{
    std::string hostPort, username, password;
    iss >> hostPort >> username >> password;

    std::string host = hostPort.substr(0, hostPort.find(':'));
    short port = std::stoi(hostPort.substr(hostPort.find(':') + 1));

    Frame loginFrame = protocol.createConnectFrame(host, username, password);
    enqueueFrame(loginFrame);
}

void KeyboardInput::handleLogout()
{
    Frame logoutFrame = protocol.createDisconnectFrame(1);
    enqueueFrame(logoutFrame);
    running.store(false);
}

void KeyboardInput::handleJoin(std::istringstream &iss)
{
    std::string channelName;
    iss >> channelName;

    int subscriptionId = protocol.getNextSubscriptionId();
    protocol.addSubscription(subscriptionId, channelName);

    Frame subscribeFrame = protocol.createSubscribeFrame(channelName, subscriptionId);
    enqueueFrame(subscribeFrame);
}

void KeyboardInput::handleExit(std::istringstream &iss)
{
    std::string channelName;
    iss >> channelName;

    int subscriptionId = protocol.getSubscriptionIdByChannel(channelName);
    if (subscriptionId != -1)
    {
        Frame unsubscribeFrame = protocol.createUnsubscribeFrame(subscriptionId);
        protocol.removeSubscription(subscriptionId);
        enqueueFrame(unsubscribeFrame);
    }
    else
    {
        std::cerr << "[ERROR] Not subscribed to channel: " << channelName << std::endl;
    }
}

void KeyboardInput::handleReport(std::istringstream &iss)
{
    std::string filePath;
    iss >> filePath;

    names_and_events events = parseEventsFile(filePath);
    for (const auto &event : events.events)
    {
        Frame sendFrame = protocol.createSendFrame(events.channel_name, event);
        enqueueFrame(sendFrame);
    }
}

void KeyboardInput::enqueueFrame(const Frame &frame)
{
    std::lock_guard<std::mutex> lock(queueMutex);
    userToServerQueue.push(frame);
}