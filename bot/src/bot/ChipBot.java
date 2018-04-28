/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package bot;

import ai.abstraction.AbstractAction;
import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.Harvest;
import ai.abstraction.pathfinding.FloodFillPathFinding;
import ai.core.AI;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.ParameterSpecification;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import rts.GameState;
import rts.PhysicalGameState;
import rts.Player;
import rts.PlayerAction;
import rts.units.*;

/**
 * Based on CRush_V1
 * @author Cristiano D'Angelo
 */

// create the variables used for units
public class ChipBot extends AbstractionLayerAI {

    Random r = new Random();
    protected UnitTypeTable utt;
    UnitType workerType;
    UnitType baseType;
    UnitType barracksType;
    UnitType rangedType;
    UnitType heavyType;
    UnitType lightType;

	//set path finding to use floodfill
	public ChipBot(UnitTypeTable a_utt) 
	{
        this(a_utt, new FloodFillPathFinding());
    }

    public ChipBot(UnitTypeTable a_utt, PathFinding a_pf) 
    {
        super(a_pf);
        reset(a_utt);
    }

    public void reset() {
        super.reset();
    }

    
    //declare what unit type to get when a variable is used
    public void reset(UnitTypeTable a_utt) {
        utt = a_utt;
        workerType = utt.getUnitType("Worker");
        baseType = utt.getUnitType("Base");
        barracksType = utt.getUnitType("Barracks");
        rangedType = utt.getUnitType("Ranged");
        lightType = utt.getUnitType("Light");
        heavyType = utt.getUnitType("Heavy");
    }

    //set variables to be used in main game by ChipBot
    public AI clone() 
    {
        return new ChipBot(utt, pf);
    }

    boolean buildingRacks = false;
    int resourcesUsed = 0;
	int ranged = 0;
	int light = 0;
    
	// Tells the units what to do in certain circumstances
    public PlayerAction getAction(int player, GameState gs) 
    {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Player p = gs.getPlayer(player);
        boolean isRush = false;
        
        //If the width * height is less than or equal to 144 set isRush as true
        if ((pgs.getWidth() * pgs.getHeight()) <= 144){
            isRush = true;
        }
        
        // Get all workers that belong to ChipBot and add them to a list 
        List<Unit> workers = new LinkedList<Unit>();
        for (Unit u : pgs.getUnits()) 
        {
            if (u.getType().canHarvest
                    && u.getPlayer() == player) 
            {
                workers.add(u);
            }
        }
        
        //if isRush was set to true change what behaviours to worker units should use
        if(isRush)
        {
            rushWorkersBehavior(workers, p, pgs, gs);
        } 
        else 
        {
            workersBehavior(workers, p, pgs, gs);
        }

        // get the bases controlled by ChipBot and if isRush is true change behaviour of base
        for (Unit u : pgs.getUnits()) 
        {
            if (u.getType() == baseType
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) 
            {
                
                if(isRush)
                {
                    rushBaseBehavior(u, p, pgs);
                }
                else 
                {
                    baseBehavior(u, p, pgs);
                }
            }
        }

        // get barracks and set behaviour
        for (Unit u : pgs.getUnits()) 
        {
            if (u.getType() == barracksType
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) 
            {
                barracksBehavior(u, p, pgs);
            }
        }

        // get unit types and set behaviours
        for (Unit u : pgs.getUnits()) 
        {
            if (u.getType().canAttack && !u.getType().canHarvest
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) 
            {
                if (u.getType() == rangedType) 
                {
                    rangedUnitBehavior(u, p, gs);
                } 
                if (u.getType() == lightType)
                {
                	lightUnitBehaviour(u, p, gs);
                }
                else 
                {
                    meleeUnitBehavior(u, p, gs);
                }
            }
        }

        return translateActions(player, gs);
    }

    // the variables keep track of how many types of unit owned by ChipBot are in game
	public void baseBehavior(Unit u, Player p, PhysicalGameState pgs) 
	{
        int nbases = 0;
        int nbarracks = 0;
        int nworkers = 0;
        int resources = p.getResources();

        for (Unit u2 : pgs.getUnits()) 
        {
            if (u2.getType() == workerType
                    && u2.getPlayer() == p.getID()) 
            {
                nworkers++;
            }
            if (u2.getType() == barracksType
                    && u2.getPlayer() == p.getID()) 
            {
                nbarracks++;
            }
            if (u2.getType() == baseType
                    && u2.getPlayer() == p.getID()) 
            {
                nbases++;
            }
        }
        
        //If there is less workers than the amount of bases plus 1 train another worker
        if (nworkers < (nbases +1) && p.getResources() >= workerType.cost) {
            train(u, workerType);
        }

        //Buffers the resources that are being used for barracks
        if (resourcesUsed != barracksType.cost * nbarracks) {
            resources = resources - barracksType.cost;
        }

        //if there are equal or more resources than worker cost + ranged cost make a worker unit
        if (buildingRacks && (resources >= workerType.cost + rangedType.cost)) 
        {
            train(u, workerType);
        }
    }

	//Build ranged or Light units depending how many of each their are in the level
    public void barracksBehavior(Unit u, Player p, PhysicalGameState pgs) 
    {
        if (p.getResources() >= rangedType.cost
        		&& ranged <= light) 
        {
           train(u, rangedType);
           ranged++;
        }
        else if (p.getResources() >= lightType.cost
        		&& light < ranged)
        {
        	train(u, lightType);
        	light++;
        }
    }

    //find the closest unit that does not belong to ChipBot and attack it (melee Unit)
    public void meleeUnitBehavior(Unit u, Player p, GameState gs) 
    {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestEnemy = null;
        int closestDistance = 0;
        for (Unit u2 : pgs.getUnits()) 
        {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) 
            {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (closestEnemy == null || d < closestDistance) 
                {
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }
        }
        
        if (closestEnemy != null) 
        {
            attack(u, closestEnemy);
        }
    }
    
    //find the closest unit that does not belong to ChipBot and attack it (light unit)
    private void lightUnitBehaviour(Unit u, Player p, GameState gs) 
    {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestEnemy = null;
        Unit closestRacks = null;
        int closestDistance = 0;
        for (Unit u2 : pgs.getUnits()) 
        {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) 
            {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (closestEnemy == null || d < closestDistance) 
                {
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }
            if (u2.getType() == barracksType && u2.getPlayer() == p.getID()) 
            {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (closestRacks == null || d < closestDistance) 
                {
                    closestRacks = u2;
                    closestDistance = d;
                }
            }
        }
        if (closestEnemy != null)
        {
            rangedAttack(u, closestEnemy, closestRacks);

        }
	}

    //find the closest unit that does not belong to ChipBot and attack it (ranged unit)
    public void rangedUnitBehavior(Unit u, Player p, GameState gs) 
    {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestEnemy = null;
        Unit closestRacks = null;
        int closestDistance = 0;
        for (Unit u2 : pgs.getUnits()) 
        {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) 
            {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (closestEnemy == null || d < closestDistance) 
                {
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }
            if (u2.getType() == barracksType && u2.getPlayer() == p.getID()) 
            {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (closestRacks == null || d < closestDistance) 
                {
                    closestRacks = u2;
                    closestDistance = d;
                }
            }
        }
        if (closestEnemy != null) 
        {
            rangedAttack(u, closestEnemy, closestRacks);

        }
    }
    
    //find how many nasses, barracks and workers there are and add them to list
    public void workersBehavior(List<Unit> workers, Player p, PhysicalGameState pgs, GameState gs) 
    {
        int nbases = 0;
        int nbarracks = 0;
        int nworkers = 0;
        resourcesUsed = 0;
        
        List<Unit> freeWorkers = new LinkedList<Unit>();
        List<Unit> battleWorkers = new LinkedList<Unit>();

        for (Unit u2 : pgs.getUnits()) 
        {
            if (u2.getType() == baseType
                    && u2.getPlayer() == p.getID()) 
            {
                nbases++;
            }
            if (u2.getType() == barracksType
                    && u2.getPlayer() == p.getID()) 
            {
                nbarracks++;
            }
            if (u2.getType() == workerType
                    && u2.getPlayer() == p.getID()) 
            {
                nworkers++;
            }
        }

        // if there is more workers than bases make them battle workers
        if (workers.size() > (nbases)) 
        {
            for (int n = 0; n < (nbases); n++) 
            {
                freeWorkers.add(workers.get(0));
                workers.remove(0);
            }
            battleWorkers.addAll(workers);
        } 
        else 
        {
            freeWorkers.addAll(workers);
        }

        if (workers.isEmpty()) 
        {
            return;
        }

        //when building a base check if there's free workers and get the X, Y to add to reserved positions
        List<Integer> reservedPositions = new LinkedList<Integer>();
        if (nbases == 0 && !freeWorkers.isEmpty())
        {
            if (p.getResources() >= baseType.cost) 
            {
                Unit u = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(u, baseType, u.getX(), u.getY(), reservedPositions, p, pgs);
            }
        }
        
        //when building a barrack check if there's free workers and get the X, Y to add to reserved positions
        if ((nbarracks == 0) && (!freeWorkers.isEmpty()) && nworkers > 0
                && p.getResources() >= barracksType.cost) 
        {
            int resources = p.getResources();
            Unit u = freeWorkers.remove(0);   
            buildIfNotAlreadyBuilding(u,barracksType,u.getX(),u.getY(),reservedPositions,p,pgs);
            resourcesUsed += barracksType.cost;
            buildingRacks = true;
        } 
        else 
        {
            resourcesUsed =  barracksType.cost * nbarracks;
        }
        
        //if there is more than 0 barracks stop building more
        if (nbarracks > 0) 
        {
            buildingRacks = true;
        }

        // if the unit is a battle worker use melee unit behaviour
        for (Unit u : battleWorkers) 
        {
            meleeUnitBehavior(u, p, gs);
        }

        // harvest with all the free non battle workers
        for (Unit u : freeWorkers) 
        {
            Unit closestBase = null;
            Unit closestResource = null;
            int closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) 
            {
                if (u2.getType().isResource) 
                {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (closestResource == null || d < closestDistance) 
                    {
                        closestResource = u2;
                        closestDistance = d;
                    }
                }
            }
            
            //find the closest base
            closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) 
            {
                if (u2.getType().isStockpile && u2.getPlayer() == p.getID()) 
                {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (closestBase == null || d < closestDistance) 
                    {
                        closestBase = u2;
                        closestDistance = d;
                    }
                }
            }
            
            //harvest from the closest base
            if (closestResource != null && closestBase != null) {
                AbstractAction aa = getAbstractAction(u);
                if (aa instanceof Harvest) {
                    Harvest h_aa = (Harvest) aa;
                    if (h_aa.getTarget() != closestResource || h_aa.getBase() != closestBase) {
                        harvest(u, closestResource, closestBase);
                    }
                } else {
                    harvest(u, closestResource, closestBase);
                }
            }
        }
    }
    
    //if isRush set to true and there are enough resources train only workers
    public void rushBaseBehavior(Unit u,Player p, PhysicalGameState pgs) 
    {
        if (p.getResources()>=workerType.cost) train(u, workerType);
    }
    
    // add all the workers and bases owned by ChipBot to list
    public void rushWorkersBehavior(List<Unit> workers, Player p, PhysicalGameState pgs, GameState gs) {
        int nbases = 0;
        int nworkers = 0;
        resourcesUsed = 0;
        
        List<Unit> freeWorkers = new LinkedList<Unit>();
        List<Unit> battleWorkers = new LinkedList<Unit>();

        for (Unit u2 : pgs.getUnits()) 
        {
            if (u2.getType() == baseType
                    && u2.getPlayer() == p.getID()) 
            {
                nbases++;
            }
            if (u2.getType() == workerType
                    && u2.getPlayer() == p.getID()) 
            {
                nworkers++;
            }
        }
        //make all the free workers battle workers
        if (p.getResources() == 0)
        {
            battleWorkers.addAll(workers);
        } 
        else if (workers.size() > (nbases)) 
        {
            for (int n = 0; n < (nbases); n++) 
            {
                freeWorkers.add(workers.get(0));
                workers.remove(0);
            }
            battleWorkers.addAll(workers);
        }
        else 
        {
            freeWorkers.addAll(workers);
        }

        if (workers.isEmpty()) 
        {
            return;
        }

        List<Integer> reservedPositions = new LinkedList<Integer>();
        if (nbases == 0 && !freeWorkers.isEmpty()) {
            // build a base:
            if (p.getResources() >= baseType.cost) {
                Unit u = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(u, baseType, u.getX(), u.getY(), reservedPositions, p, pgs);
                //resourcesUsed += baseType.cost;
            }
        }
        
        // all battle workers use melee unit behaviour
        for (Unit u : battleWorkers) 
        {
            meleeUnitBehavior(u, p, gs);
        }

        // find the closest base
        for (Unit u : freeWorkers) 
        {
            Unit closestBase = null;
            Unit closestResource = null;
            int closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) 
            {
                if (u2.getType().isResource) 
                {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (closestResource == null || d < closestDistance) 
                    {
                        closestResource = u2;
                        closestDistance = d;
                    }
                }
            }
            closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) 
            {
                if (u2.getType().isStockpile && u2.getPlayer() == p.getID()) 
                {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (closestBase == null || d < closestDistance) 
                    {
                        closestBase = u2;
                        closestDistance = d;
                    }
                }
            }
            
            //harvest from the closest base
            if (closestResource != null && closestBase != null) 
            {
                AbstractAction aa = getAbstractAction(u);
                if (aa instanceof Harvest) 
                {
                    Harvest h_aa = (Harvest) aa;
                    if (h_aa.getTarget() != closestResource || h_aa.getBase() != closestBase) 
                    {
                        harvest(u, closestResource, closestBase);
                    }
                } 
                else 
                {
                    harvest(u, closestResource, closestBase);
                }
            }
        }
    }
    
    
    public void rangedAttack(Unit u, Unit target, Unit racks) 
    {
        actions.put(u, new RangedAttack(u, target, racks, pf));
    }
    
    

    @Override
    public List<ParameterSpecification> getParameters() {
        List<ParameterSpecification> parameters = new ArrayList<>();

        parameters.add(new ParameterSpecification("PathFinding", PathFinding.class, new FloodFillPathFinding()));

        return parameters;
    }
}