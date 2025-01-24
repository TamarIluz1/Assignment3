
#include "../include/Frame.h"
#include <sstream>
#include <stdexcept>

Frame::Frame(const std::string &command) : command(command) {}

// Frame* Frame::clone() const
// {
//     Frame *frame = new Frame(command);
//     frame->headers = headers;
//     frame->body = body;
//     return frame;
// }

Frame::~Frame() {}
void Frame::addHeader(const std::string &key, const std::string &value)
{
    headers[key] = value;
}

void Frame::setBody(const std::string &body)
{
    this->body = body;
}

std::string Frame::getCommand() const
{
    return command;
}

std::map<std::string, std::string> Frame::getHeaders() const
{
    return headers;
}

std::string Frame::getHeader(const std::string &key) const
{
    auto it = headers.find(key);
    return (it != headers.end()) ? it->second : "";
}

std::string Frame::getBody() const
{
    return body;
}

Frame Frame::parseFrame(const std::string &rawFrame)
{
    std::istringstream stream(rawFrame);
    std::string line, command;
    std::getline(stream, command);
    Frame frame(command);

    while (std::getline(stream, line) && !line.empty())
    {
        auto delimiterPos = line.find(':');
        if (delimiterPos != std::string::npos)
        {
            std::string key = line.substr(0, delimiterPos);
            std::string value = line.substr(delimiterPos + 1);
            frame.addHeader(key, value);
        }
    }

    std::string body;
    while (std::getline(stream, line))
        body += line + "\n";

    if (!body.empty())
        frame.setBody(body.substr(0, body.size() - 1));
    return frame;
}

std::string Frame::toString() const
{
    std::ostringstream sb;
    sb << command << "\n";
    for (const auto &header : headers)
        sb << header.first << ":" << header.second << "\n";

    if (!body.empty())
        sb << "\n"
           << body;
    return sb.str();
}

std::string Frame::getUserNameFromBody() const
{
    std::istringstream bodyStream(this->getBody());
    std::string line;
    std::string user;

    while (std::getline(bodyStream, line))
    {
        if (line.find("user:") == 0)
        {
            user = line.substr(5); // Extract the value after "user:"
            break;
        }
    }
    return user;
}