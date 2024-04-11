<h1>Final project for MSAI 6125 Multi-agent System</h1>
<p>
<h2>Implementations</h2>
<p>
<b>MyPlanner.class</b>:
Core logic. Yield the next action of an agent in each time step. 
<br>
<b>MyAgent.class</b>:
Simply execute the action returned by MyPlanner.
<br>
<b>MyMemory.class</b>:
Store position of the fuel station and the information to share in each time step.
<br>
<b>MyMessage.class</b>:
Simple getter functions. 
<br>
<b>Region.class</b>:
Core logic. Decide the next movement of an agent inside its region.
<br>
<b>Strategy.class</b>:
An enum class of five possible states of an agent.  
</p>

<h2>Evaluations</h2>
<b>Five agents namely "Agent1" to "Agent5" in the tileworld environment. </b>
<br>
Avg total score in config1 is around 705.
<br>
Avg total score in config2 is around 945.
<br>
Avg total score in config3 with x,y=100 & mean=1 & dev=0.5 & lifeTime=50 is arount 535.
