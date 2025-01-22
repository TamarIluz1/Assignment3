
#pragma once

#include <string>
#include <iostream>
#include <map>
#include <vector>
#include "../include/StompProtocol.h"
#include "../include/ConnectionHandler.h"

class KeyboardInput
{
private:
    StompProtocol &protocol;
    std::queue<Frame> &userToServerQueue;
    std::mutex &queueMutex;
    std::atomic<bool> &running;
    std::condition_variable &queueCondition;

    // Command Handlers
    void handleLogin(std::istringstream &iss);
    void handleLogout();
    void handleJoin(std::istringstream &iss);
    void handleExit(std::istringstream &iss);
    void handleReport(std::istringstream &iss);
    void enqueueFrame(const Frame &frame);

public:
    KeyboardInput(StompProtocol &protocol, std::queue<Frame> &queue, std::mutex &mutex,
                  std::atomic<bool> &running, std::condition_variable &condition);
    void start();
};