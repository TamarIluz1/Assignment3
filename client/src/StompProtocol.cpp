
#include "../include/StompProtocol.h"
#include <sstream>
#include <iostream>
#include <queue>
#include <mutex>
#include "../include/Frame.h"

#include "../include/StompProtocol.h"
#include <sstream>
#include <iostream>

StompProtocol::StompProtocol()
    : username(""), subscriptions(), nextSubscriptionId(-1), activeConnectionHandler(nullptr),
      connectionActive(false), channelUserEvents(), lastReceiptId(0), reciptCounter(0),
      subscriptionsMutex(), eventsMutex(), connectionMutex() {}

StompProtocol::~StompProtocol() { clearConnectionHandler(); }

// StompProtocol::StompProtocol(const StompProtocol &other)
//     : username(other.username), nextSubscriptionId(other.nextSubscriptionId),
//       activeConnectionHandler(other.activeConnectionHandler), connectionActive(other.connectionActive),
//       lastReceiptId(other.lastReceiptId), reciptCounter(other.reciptCounter),
//       subscriptions(other.subscriptions), channelUserEvents(other.channelUserEvents) {}

// StompProtocol::StompProtocol(const StompProtocol &other)
//     : username(other.username),
//       subscriptions(other.subscriptions),
//       nextSubscriptionId(other.nextSubscriptionId),
//       activeConnectionHandler(other.activeConnectionHandler), // Be cautious with raw pointers
//       connectionActive(other.connectionActive),
//       channelUserEvents(other.channelUserEvents),
//       lastReceiptId(other.lastReceiptId),
//       reciptCounter(other.reciptCounter),
//       subscriptionsMutex(), // Explicitly default-initialize mutexes
//       eventsMutex(),
//       connectionMutex()
// {
// }

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
    return connectionActive;
}

void StompProtocol::setUsername(const std::string &user)
{
    username = user;
}

int StompProtocol::getReciptCounter() const
{
    return reciptCounter;
}

void StompProtocol::setReciptCounter(int reciptCounter)
{
    this->reciptCounter = reciptCounter;
}

int StompProtocol::getNextSubscriptionId() const
{

    return nextSubscriptionId;
}

void StompProtocol::setNextSubscriptionId(int nextSubscriptionId)
{
    this->nextSubscriptionId = nextSubscriptionId;
}

void StompProtocol::setLastReceiptId(int lastReceiptId)
{
    this->lastReceiptId = lastReceiptId;
}

int StompProtocol::getLastReceiptId() const
{
    return lastReceiptId;
}

std::string StompProtocol::getUsername() const
{
    return username;
}

Frame StompProtocol::createConnectFrame(const std::string &host, const std::string &username, const std::string &password)
{
    Frame frame("CONNECT");
    frame.addHeader("accept-version", "1.2");
    frame.addHeader("host", host);
    frame.addHeader("login", username);
    frame.addHeader("passcode", password);
    return frame;
}

Frame StompProtocol::createSubscribeFrame(const std::string &channelName, int subscriptionId)
{
    Frame frame("SUBSCRIBE");
    frame.addHeader("destination", channelName);
    frame.addHeader("id", std::to_string(subscriptionId));
    frame.addHeader("receipt", std::to_string(reciptCounter));
    reciptCounter++;
    return frame;
}

Frame StompProtocol::createUnsubscribeFrame(int subscriptionId)
{
    Frame frame("UNSUBSCRIBE");
    frame.addHeader("id", std::to_string(subscriptionId));
    frame.addHeader("receipt", std::to_string(reciptCounter));
    reciptCounter++;
    return frame;
}

Frame StompProtocol::createSendFrame(const std::string &channelName, const Event &event)
{
    Frame frame("SEND");
    frame.addHeader("destination", "/" + channelName);
    std::ostringstream frameBodyStream;
    frameBodyStream << "user:" << event.getEventOwnerUser() << "\n";
    frameBodyStream << "city:" << event.get_city() << "\n";
    frameBodyStream << "event name:" << event.get_name() << "\n";
    frameBodyStream << "date time:" << std::to_string(event.get_date_time()) << "\n";
    frameBodyStream << "general information:\n";
    for (const auto &info : event.get_general_information())
    {
        frameBodyStream << "  " << info.first << ":" << info.second << "\n";
    }
    frameBodyStream << "description:" << event.get_description() << "\n";

    frame.setBody(frameBodyStream.str());

    return frame;
}

Frame StompProtocol::createDisconnectFrame()
{
    Frame frame("DISCONNECT");
    frame.addHeader("receipt", std::to_string(reciptCounter));
    lastReceiptId = reciptCounter;
    reciptCounter++;

    return frame;
}

void StompProtocol::processFrame(const Frame &frame, std::atomic<bool> &disconnectReceived)
{
    std::string command = frame.getCommand();
    if (command == "MESSAGE")
    {
        Event event(frame.getBody());
        std::string channelName = frame.getHeader("destination");
        std::string user = frame.getUserNameFromBody();
        storeEvent(channelName.substr(1), user, event);
        std::cout << "[SERVER MESSAGE] " << frame.toString() << std::endl;
    }
    else if (command == "ERROR")
    {
        std::cerr << "ERROR FROM THE SERVER:\n\n " << frame.toString() << std::endl;
        std::lock_guard<std::mutex> subscriptionsLock(subscriptionsMutex);
        std::lock_guard<std::mutex> eventsLock(eventsMutex);
        std::lock_guard<std::mutex> connectionLock(connectionMutex);
        subscriptions.clear();
        channelUserEvents.clear();
        connectionActive = false;

        std::cerr << "[SERVER Disconnected the Client] " << std::endl;

        disconnectReceived.store(true);
    }
    else if (command == "RECEIPT")
    {
        std::cout << "[SERVER RECEIPT] " << frame.toString() << std::endl;
        if (frame.getHeader("receipt-id") == std::to_string(lastReceiptId))
        {
            std::lock_guard<std::mutex> subscriptionsLock(subscriptionsMutex);
            std::lock_guard<std::mutex> eventsLock(eventsMutex);
            std::lock_guard<std::mutex> connectionLock(connectionMutex);
            subscriptions.clear();
            channelUserEvents.clear();
            connectionActive = false;

            std::cerr << "[SERVER Disconnected the Client] " << std::endl;

            disconnectReceived.store(true);
        }
    }
}

void StompProtocol::addSubscription(int id, const std::string &channel)
{
    std::lock_guard<std::mutex> lock(subscriptionsMutex);
    subscriptions[id] = channel;
}

void StompProtocol::removeSubscription(int id)
{
    std::lock_guard<std::mutex> lock(subscriptionsMutex);
    subscriptions.erase(id);
}

int StompProtocol::getNextSubscriptionId()
{
    nextSubscriptionId++;
    return nextSubscriptionId;
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

void StompProtocol::storeEvent(const std::string &channelName, const std::string &user, const Event &event)
{
    std::lock_guard<std::mutex> lock(eventsMutex);
    channelUserEvents[channelName][user].push_back(event);
}

std::vector<Event> StompProtocol::getEventsForSummary(const std::string &channelName, const std::string &user) const
{
    auto channelIt = channelUserEvents.find(channelName);
    if (channelIt != channelUserEvents.end())
    {
        auto userIt = channelIt->second.find(user);
        if (userIt != channelIt->second.end())
        {
            return userIt->second;
        }
    }
    return {};
}

void StompProtocol::clearEventsInChannel(const std::string &channelName)
{
    std::lock_guard<std::mutex> lock(eventsMutex);
    channelUserEvents[channelName].clear();
}