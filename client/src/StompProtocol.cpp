
#include "../include/StompProtocol.h"
#include <sstream>
#include <iostream>
#include <queue>
#include <mutex>

// Constructor
StompProtocol::StompProtocol() : nextSubscriptionId(0), activeConnectionHandler(nullptr), connectionActive(false) {}

// Connection Management
void StompProtocol::setActiveConnectionHandler(ConnectionHandler *handler)
{
    std::lock_guard<std::mutex> lock(connectionMutex);
    activeConnectionHandler = handler;
    connectionActive = true;
}

void StompProtocol::clearConnectionHandler()
{
    std::lock_guard<std::mutex> lock(connectionMutex);
    if (activeConnectionHandler)
    {
        activeConnectionHandler->close();
        delete activeConnectionHandler;
        activeConnectionHandler = nullptr;
    }
    connectionActive = false;
}

ConnectionHandler *StompProtocol::getActiveConnectionHandler()
{
    std::lock_guard<std::mutex> lock(connectionMutex);
    return activeConnectionHandler;
}

bool StompProtocol::isConnectionActive()
{
    std::lock_guard<std::mutex> lock(connectionMutex);
    return connectionActive;
}

// Frame Creation
Frame StompProtocol::createConnectFrame(const std::string &host, const std::string &username, const std::string &password)
{
    Frame frame("CONNECT");
    frame.addHeader("accept-version", "1.2");
    frame.addHeader("host", "stomp.cs.bgu.ac.il");
    frame.addHeader("login", username);
    frame.addHeader("passcode", password);
    return frame;
}

Frame StompProtocol::createSubscribeFrame(const std::string &channelName, int subscriptionId)
{
    Frame frame("SUBSCRIBE");
    frame.addHeader("destination", channelName);
    frame.addHeader("id", std::to_string(subscriptionId));
    return frame;
}

Frame StompProtocol::createUnsubscribeFrame(int subscriptionId)
{
    Frame frame("UNSUBSCRIBE");
    frame.addHeader("id", std::to_string(subscriptionId));
    return frame;
}

Frame StompProtocol::createSendFrame(const std::string &channelName, const Event &event)
{
    Frame frame("SEND");
    frame.addHeader("destination", channelName);
    frame.setBody(event.toString());
    return frame;
}

Frame StompProtocol::createDisconnectFrame(int receiptId)
{
    Frame frame("DISCONNECT");
    frame.addHeader("receipt", std::to_string(receiptId));
    return frame;
}

// Frame Processing
void StompProtocol::processFrame(const Frame &frame)
{
    std::string command = frame.getCommand();
    if (command == "MESSAGE")
    {
        std::cout << "[SERVER MESSAGE] " << frame.getBody() << std::endl;
    }
    else if (command == "ERROR")
    {
        std::cerr << "[SERVER ERROR] " << frame.getHeader("message") << std::endl;
    }
    else if (command == "RECEIPT")
    {
        std::cout << "[SERVER RECEIPT] " << frame.getHeader("receipt-id") << std::endl;
    }
}

void StompProtocol::setUsername(const std::string &user)
{
    username = user;
}

// Subscription Management
void StompProtocol::addSubscription(int id, const std::string &channel)
{
    subscriptions[id] = channel;
}

void StompProtocol::removeSubscription(int id)
{
    subscriptions.erase(id);
}

int StompProtocol::getNextSubscriptionId()
{
    return nextSubscriptionId++;
}

int StompProtocol::getSubscriptionIdByChannel(const std::string &channelName) const
{
    for (const auto &entry : subscriptions)
    {
        if (entry.second == channelName)
        {
            return entry.first;
        }
    }
    return -1;
}