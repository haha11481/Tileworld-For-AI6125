Code for AI6125 final Project.

Main implementations:

1.MyAgent.class: 

2.MyMemory.class: How agents percept the world and update their perception based on information gained in each time step.

3.MyPlanner.class: How agents generate the plan of their next move based on:

(1) Environment parameters

(2) Sensed Objects

(3) Current status

(4) Other agents' messages

Includes Region.class to mark the working area of each agent.

4.MyMessage.class: Which kind of information the agents would like to share with each other.

[TODO] Core parts to be implemented:

1.Optimize explorative strategy at early stages that tend to find the fuel station. 
Current strategy: 

move to its own region -> move to the leftmost -> (move up or down until upper area & lower area are scanned -> move to the right)* -> repeate * until entire region is scanned

2.Add planning strategy for multi agents. Need to consider the states and positions of other nearby agents.

3.Routing algorithm needs to remove invalid objects (i.e. those who will run out of time when the agent arrive at them).

4.Distance calculation needs to consider obstacles instead of only Euclidean distance (but can we just use the AstarPathGenerator and take the length of the generated path as distance?)

5.more and more
