# Pre-requisites
* Java 1.8/1.11/1.15
* Maven

# Problem Statement 

Asked in PhonePe Machine Coding Round

    It was on App Signal Coding Platform
    Design a system which stores and manages different types of campaigns.

    There can be 2 types of campaign

    Communication campaign
    Ad Campaign
    For this problem, we'll only focus on building communication campaigns, but the campaign is expected to be extensible in future for any other types of campaign.

    In any campaign, there should be campaignId, name, description etc

    The communication campaign will have a list of communications to be sent.
    A communication can be sent to the following channels

    SMS - Here, only a message is enough.
            EMAIL - In this, mail subject and actual message is required.
    For any campaign, order of sending communication is important i.e few communication needs to be sent before other communications.
    For example:
    Assume there are 4 communications (Let's say - A, B, C, D)
            A -> B
    B -> C
    A -> C
    Here few conclusions can be made

    D is independent, so it can be sent anytime
    A needs to be sent before B and C
    C can only be sent after A and B.
            Mandatory Implementations

    createCampaign(Campaign campaign)
    This function is to create and store the campaign.
    It can only be created if it passes all necessary validations.
    Also check if the order of sending communication is possible to be followed, else throw necessary exceptions.

    getCampaign(String campaignId)
    To get the campaign details using campaign Id

    evaluateCampaign(String campaignId)
    This is to evaluate the campaign and return the order of execution of the communications
    For above sample example (A, B, C, D), Response will look something like
[{
“rank”: 1,
“communicationId”: “D”
“channel”: “SMS”
“message”: “You can do this!”
},{
“rank”: 2,
“communicationId”: “A”
“channel”: “EMAIL”,
“subject”: “Easy peasy”
“message”: “You can do this!”
},{
“rank”: 3,
“communicationId”: “B”
“channel”: “SMS”
“message”: “You can do this!”
},
{
“rank”: 4,
“communicationId”: “C”
“channel”: “SMS”
“message”: “You can do this!”
}]

    Points to note

    All necessary validations must be present.
    In case of an exception, proper errorCode must be present.
    Use In Memory database to store the campaign
    Your code should cover all the mandatory functionalities explained above.
    Your code should be executable and clean.
    How will you be evaluated?

    Code should be working.
    Code readability and testability
    Separation Of Concerns
    Object-Oriented concepts.
    Language proficiency.
    Proper Algorithm and DS choices
    SOLID principles


# How to run the code
execute the Main.java file