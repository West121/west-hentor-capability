package com.sgs.capability.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** User favorite group for saved ability rows. */
public class FavoriteGroup {
    public UUID id;
    public String name;
    public long userId;
    public List<UUID> abilityIds = new ArrayList<>();
}
