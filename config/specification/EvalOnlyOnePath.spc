CONTROL AUTOMATON EvalOnlyOnePathAutomaton

INITIAL STATE Init;

STATE USEFIRST Init :
   MATCH EXIT -> BREAK;
   TRUE -> GOTO Init;

END AUTOMATON
