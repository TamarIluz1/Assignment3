#pragma once

#include <string>
#include <map>
#include <queue>
#include <mutex>
#include "../include/Frame.h" // Ensure Frame.h defines the Frame class
#include "../include/ConnectionHandler.h"
#include "../include/event.h"
#include "../include/StompProtocol.h"

class StompProtocol
{
private:
    std::string username;
    std::map<int, std::string> subscriptions; // Maps subscription IDs to channel names
    int nextSubscriptionId;
    ConnectionHandler *activeConnectionHandler;
    bool connectionActive;
    std::map<std::string, std::map<std::string, std::vector<Event>>> channelUserEvents;
    int lastReceiptId;
    int reciptCounter;

    mutable std::mutex subscriptionsMutex;
    mutable std::mutex eventsMutex;
    mutable std::mutex connectionMutex;

public:
    StompProtocol();
    StompProtocol(const StompProtocol &other) = delete;
    StompProtocol &operator=(const StompProtocol &) = delete;

    ~StompProtocol();

    void setActiveConnectionHandler(ConnectionHandler *handler);
    void clearConnectionHandler();
    ConnectionHandler *getActiveConnectionHandler();
    bool isConnectionActive();
    void setUsername(const std::string &user);
    std::string getUsername() const;
    int getReciptCounter() const;
    void setReciptCounter(int reciptCounter);
    int getNextSubscriptionId() const;
    void setNextSubscriptionId(int reciptCounter);
    void setLastReceiptId(int lastReceiptId);
    int getLastReceiptId() const;

    // Frame creation
    Frame createConnectFrame(const std::string &host, const std::string &username, const std::string &password);
    Frame createSubscribeFrame(const std::string &channelName, int subscriptionId);
    Frame createUnsubscribeFrame(int subscriptionId);
    Frame createSendFrame(const std::string &channelName, const Event &event);
    Frame createDisconnectFrame();

    // Frame processing
    void processFrame(const Frame &frame, std::atomic<bool> &disconnectReceived);

    // Subscription management
    void addSubscription(int id, const std::string &channel);
    void removeSubscription(int id);
    int getNextSubscriptionId();
    int getSubscriptionIdByChannel(const std::string &channelName) const;

    void storeEvent(const std::string &channelName, const std::string &user, const Event &event);
    std::vector<Event> getEventsForSummary(const std::string &channelName, const std::string &user) const;
    void clearEventsInChannel(const std::string &channelName);
};
