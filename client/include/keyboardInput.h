#pragma once

#include <queue>
#include <mutex>
#include <condition_variable>
#include <atomic>
#include <string>
#include "../include/StompProtocol.h"
#include "../include/Frame.h"

class KeyboardInput
{
private:
    StompProtocol &protocol;
    std::atomic<bool> &running;
    std::atomic<bool> &disconnectReceived;

    void processCommand(const std::string &input);

public:
    KeyboardInput(StompProtocol &protocol, std::atomic<bool> &running, std::atomic<bool> &disconnectReceived);
    void start();
    void createDirectoryIfNotExists(const std::string &dirPath);
    // Function to get the current working directory
    std::string getCurrentWorkingDir();
    std::string epochToDate(int epochTime);
    void createDirectories(const std::string &path);
};