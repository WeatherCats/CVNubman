package org.cubeville.cvnubman.nubman;

import org.cubeville.cvgames.models.PlayerState;
import org.cubeville.cvnubman.GhostType;

import java.util.HashMap;

public class NubmanState extends PlayerState {
    public boolean isNubman = false;
    public HashMap<String, Object> ghostType;
    public boolean isDead = false;
    @Override
    public int getSortingValue() {
        return 0;
    }
}
