#pragma once

#include <string>
#include <map>

class Frame
{
private:
    std::string command;
    std::map<std::string, std::string> headers;
    std::string body;

public:
    Frame(const std::string &command);
    ~Frame();

    // Methods
    void addHeader(const std::string &key, const std::string &value);
    void setBody(const std::string &body);
    std::string getCommand() const;
    std::map<std::string, std::string> getHeaders() const;
    std::string getHeader(const std::string &key) const;
    std::string getBody() const;
    static Frame parseFrame(const std::string &rawFrame);
    std::string toString();
};
