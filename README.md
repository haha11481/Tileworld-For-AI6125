Code for AI6125 final Project.

Main implementations:

1.MyAgent.class: How agents plan their next action based on their recognition of the world.
Including route planning, distance estimation, negotiation strategy, refuel strategy ...

2.MyMemory.class: How agents percept the world and update their perception based on information gained (from itself or other agents) in each time step.

3.MySensor.class: Which kind of information the agents would like to sense.

4.MyMessage.class: Which kind of information the agents would like to share with each other.

Core parts to be implemented:
1.Add explorative strategy at early stages that tend to find the fuel station.
2.Add planning strategy for multi agents. Need to consider the states and positions of other nearby agents.
3.Routing algorithm needs to remove invalid objects (i.e. those who will run out of time when the agent arrive at them).
4.Distance calculation needs to consider obstacles instead of only Euclidean distance (but can we just use the AstarPathGenerator and take the length of the generated path as distance?)
5.more and more