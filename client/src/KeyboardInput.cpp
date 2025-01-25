#include "../include/KeyboardInput.h"
#include <iostream>
#include <sstream>
#include <fstream>
#include <ctime>
#include <iomanip>
#include <filesystem>

KeyboardInput::KeyboardInput(StompProtocol &protocol, std::atomic<bool> &running, std::atomic<bool> &disconnectReceived)
    : protocol(protocol),
      running(running), disconnectReceived(disconnectReceived) {}

void KeyboardInput::start()
{
    std::string input;
    while (running.load())
    {
        if (disconnectReceived.load())
        {
            break;
        }

        std::getline(std::cin, input);
        if (input.empty())
        {
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
            continue;
        }

        try
        {
            processCommand(input);
        }
        catch (const std::exception &e)
        {
            std::cerr << "[ERROR] Failed to process input: " << e.what() << std::endl;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    std::cerr << "KeyBoard stoped " << std::endl;
}

void KeyboardInput::processCommand(const std::string &input)
{
    std::istringstream iss(input);
    std::string command;
    iss >> command;

    if (command == "login")
    {
        if (disconnectReceived.load())
        {
            // Todo add the object external to this class
            std::string hostPort, username, password;
            iss >> hostPort >> username >> password;

            std::string host = hostPort.substr(0, hostPort.find(':'));
            short port = std::stoi(hostPort.substr(hostPort.find(':') + 1));
            protocol.clearConnectionHandler();
            protocol.setActiveConnectionHandler(new ConnectionHandler(host, port));
            if (!(*protocol.getActiveConnectionHandler()).connect())
            {
                std::cerr << "”Could not connect to server" << std::endl;
                return;
            }

            protocol.setUsername(username);
            protocol.setReciptCounter(0);
            protocol.setNextSubscriptionId(0);
            protocol.setLastReceiptId(-1);

            Frame connectFrame = protocol.createConnectFrame("stomp.cs.bgu.ac.il", username, password);
            if (!(*protocol.getActiveConnectionHandler()).sendFrameAscii(connectFrame.toString(), '\0'))
            {
                std::cerr << "[ERROR] Failed to send CONNECT frame." << std::endl;
                disconnectReceived.store(false);
                return;
            }

            std::string serverResponse;
            if (!(*protocol.getActiveConnectionHandler()).getFrameAscii(serverResponse, '\0'))
            {
                std::cerr << "[ERROR] Failed to receive server response." << std::endl;
                return;
            }
            std::cout << "[SERVER MESSAGE] " << serverResponse << std::endl;

            if (serverResponse.find("CONNECTED") == 0)
            {
                std::cout << "[INFO] Login successful!" << std::endl;
            }
            else
            {
                std::cerr << "[ERROR] Login failed: " << serverResponse << std::endl;
                return;
            }

            disconnectReceived.store(false);
        }
        else
        {
            std::cerr << "[ERROR] Already logged in." << std::endl;
        }
    }
    else if (command == "join")
    {
        std::string channelName;
        iss >> channelName;
        if (protocol.getSubscriptionIdByChannel(channelName) != -1)
        {
            std::cerr << "[ERROR] Already subscribed to channel: " << channelName << std::endl;
            return;
        }

        int subscriptionId = protocol.getNextSubscriptionId();
        protocol.addSubscription(subscriptionId, channelName);

        Frame frame = protocol.createSubscribeFrame(channelName, subscriptionId);
        protocol.getActiveConnectionHandler()->sendFrameAscii(frame.toString(), '\0');

        std::cout << "[INFO] Send to Joined channel: " << channelName << std::endl;
    }
    else if (command == "exit")
    {
        std::string channelName;
        iss >> channelName;

        int subscriptionId = protocol.getSubscriptionIdByChannel(channelName);
        if (subscriptionId != -1)
        {
            Frame frame = protocol.createUnsubscribeFrame(subscriptionId);
            protocol.removeSubscription(subscriptionId);
            protocol.clearEventsInChannel(channelName);

            protocol.getActiveConnectionHandler()->sendFrameAscii(frame.toString(), '\0');

            std::cout << "[INFO] Send to to Exited channel: " << channelName << std::endl;
        }
        else
        {
            std::cerr << "[ERROR] Not subscribed to channel: " << channelName << std::endl;
        }
    }
    else if (command == "summary")
    {
        std::string channelName, user, filePath;
        iss >> channelName >> user >> filePath;

        if (protocol.getSubscriptionIdByChannel(channelName) == -1)
        {
            std::cerr << "[ERROR] You are not subscribed to the channel of the event." << std::endl;
            return;
        }

        // Get the directory path from the file path
        std::string dirPath = filePath.substr(0, filePath.find_last_of("/\\"));

        // Create directories if they do not exist
        if (!dirPath.empty())
        { // Only attempt to create if there's a valid directory path
            createDirectories(dirPath);
        }
        std::vector<Event> events(protocol.getEventsForSummary(channelName, user));

        std::ofstream file(filePath, std::ios::out);
        if (!file.is_open())
        {
            std::cerr << "[ERROR] Failed to open file: " << filePath << std::endl;
            return;
        }

        file << "Channel: " << channelName << "\n"
             << "Stats:\n"
             << "Total: " << events.size() << "\n";

        int activeCount = 0, forcesArrivalCount = 0;
        for (const Event &event : events)
        {
            const std::map<std::string, std::string> &info = event.get_general_information();
            if (info.count(" active") && info.at(" active") == "true")
            {
                activeCount++;
            }
            if (info.count(" forces_arrival_at_scene") && info.at(" forces_arrival_at_scene") == "true")
            {
                forcesArrivalCount++;
            }
        }

        file << "active: " << activeCount << "\n"
             << "Forces Arrival at Scene: " << forcesArrivalCount << "\n\n"
             << "Event Reports:\n\n";

        // sort the events by date and time and after by event name lexicographically
        std::sort(events.begin(), events.end(), [](const Event &a, const Event &b)
                  { return a.get_date_time() < b.get_date_time() || (a.get_date_time() == b.get_date_time() && a.get_name() < b.get_name()); });

        for (size_t i = 0; i < events.size(); ++i)
        {
            const Event &event = events[i];
            std::string description = event.get_description();
            std::string summaryOfDescription = description.substr(0, 27);
            if (description.size() > 27)
            {
                summaryOfDescription += "...";
            }
            else
            {
                summaryOfDescription = description;
            }
            file << "Report_" << i + 1 << ":\n"
                 << "    City: " << event.get_city() << "\n"
                 << "    Date Time: " << epochToDate(event.get_date_time()) << "\n"
                 << "    Event Name: " << event.get_name() << "\n"
                 << "    Summary: " << summaryOfDescription << "\n\n";
        }

        file.close();
        std::cout << "[INFO] Summary saved to " << filePath << std::endl;
    }
    else if (command == "logout")
    {
        Frame frame = protocol.createDisconnectFrame();
        std::cout << "[INFO] Waiting for DISCONNECT receipt..." << std::endl;
        protocol.getActiveConnectionHandler()->sendFrameAscii(frame.toString(), '\0');

        while (!disconnectReceived.load())
        {
            std::this_thread::sleep_for(std::chrono::milliseconds(200));
        }

        std::cout << "[INFO] Logged out successfully." << std::endl;
    }
    else if (command == "report")
    {

        std::string json_path;
        iss >> json_path;
        names_and_events parsedData = parseEventsFile(json_path);
        std::string channelName = parsedData.channel_name;
        if (channelName.empty())
        {
            std::cerr << "[ERROR] No channel name found in the JSON file." << std::endl;
            return;
        }

        if (protocol.getSubscriptionIdByChannel(channelName) == -1)
        {
            std::cerr << "[ERROR] You are not subscribed to the channel to send it events." << std::endl;
            return;
        }

        if (parsedData.events.empty())
        {
            std::cerr << "[ERROR] No events found in the JSON file." << std::endl;
            return;
        }

        // get a list of names of the events
        std::vector<Event> events_by_time;
        for (Event &event : parsedData.events)
        {
            events_by_time.push_back(event);
        }

        // sort the events by date the most recent event will be at the end of the list
        std::sort(events_by_time.begin(), events_by_time.end(), [](const Event &a, const Event &b)
                  { return a.get_date_time() < b.get_date_time(); });

        // send events to the server that user is subscribed to
        if (!events_by_time.empty())
        {
            for (Event &event : events_by_time)
            {

                event.setEventOwnerUser(protocol.getUsername());
                Frame frame = protocol.createSendFrame(channelName, event);
                protocol.getActiveConnectionHandler()->sendFrameAscii(frame.toString(), '\0');
                std::cout << "[INFO] Report sent to channel: " << channelName << " for event: " << event.get_name() << std::endl;
            }
            std::cout << "[INFO] Reported" << std::endl;
        }

        // // Assuming you want to send all events in the list
        // if (!parsedData.events.empty())
        // {
        //     for (Event &event : parsedData.events)
        //     {
        //         event.setEventOwnerUser(protocol.getUsername());
        //         std::string channelName = parsedData.channel_name;

        //         // Create a frame using the event
        //         Frame frame = protocol.createSendFrame(parsedData.channel_name, event);

        //         // Send the frame
        //         protocol.getActiveConnectionHandler()->sendFrameAscii(frame.toString(), '\0');
        //         std::cout << "[INFO] Report sent to channel: " << parsedData.channel_name << " for event: " << event.get_name() << std::endl;
        //     }
        //     std::cout << "[INFO] Reported" << std::endl;
        // }
        // else
        // {
        //     std::cerr << "[ERROR] No events found in the JSON file." << std::endl;
        // }
    }
    else
    {
        std::cerr << "[ERROR] Unknown command: " << command << std::endl;
    }
}

// Function to create a directory if it doesn't exist
void KeyboardInput::createDirectoryIfNotExists(const std::string &dirPath)
{
    struct stat info;
    if (stat(dirPath.c_str(), &info) != 0 || !(info.st_mode & S_IFDIR))
    {
        mkdir(dirPath.c_str(), 0777);
    }
}

// Function to get the current working directory
std::string KeyboardInput::getCurrentWorkingDir()
{
    char temp[PATH_MAX];
    return (getcwd(temp, sizeof(temp)) ? std::string(temp) : std::string(""));
}

std::string KeyboardInput::epochToDate(int epochTime)
{
    std::time_t time = epochTime;
    std::tm *tm = std::localtime(&time);
    std::ostringstream oss;
    oss << std::put_time(tm, "%d/%m/%Y %H:%M:%S");
    return oss.str();
}

// Function to create directories recursively in C++11
void KeyboardInput::createDirectories(const std::string &path)
{
    std::istringstream ss(path);
    std::string currentPath;
    std::string segment;
    while (std::getline(ss, segment, '/'))
    {
        if (!segment.empty())
        {
            currentPath += segment + "/";
            struct stat info;
            if (stat(currentPath.c_str(), &info) != 0)
            {
                // Directory does not exist; create it
                if (mkdir(currentPath.c_str(), 0777) != 0)
                {
                    std::cerr << "[ERROR] Failed to create directory: " << currentPath << std::endl;
                    return;
                }
            }
            else if (!(info.st_mode & S_IFDIR))
            {
                // Exists but not a directory
                std::cerr << "[ERROR] Path exists but is not a directory: " << currentPath << std::endl;
                return;
            }
        }
    }
}
