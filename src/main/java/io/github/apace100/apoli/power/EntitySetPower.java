package io.github.apace100.apoli.power;

import io.github.apace100.apoli.Apoli;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class EntitySetPower extends Power {

    private final Consumer<Pair<Entity, Entity>> actionOnAdd;
    private final Consumer<Pair<Entity, Entity>> actionOnRemove;

    private final Set<UUID> entityUuids = new HashSet<>();

    private final HashMap<UUID, Entity> entities = new HashMap<>();

    public EntitySetPower(PowerType<?> type, LivingEntity entity, Consumer<Pair<Entity, Entity>> actionOnAdd, Consumer<Pair<Entity, Entity>> actionOnRemove) {
        super(type, entity);
        this.actionOnAdd = actionOnAdd;
        this.actionOnRemove = actionOnRemove;
    }

    public void add(Entity entity) {
        if(entity == null || entity.isRemoved() || entity.world.isClient) {
            return;
        }
        UUID uuid = entity.getUuid();
        if(!entityUuids.contains(uuid)) {
            entityUuids.add(uuid);
            entities.put(uuid, entity);
            if(actionOnAdd != null) {
                actionOnAdd.accept(new Pair<>(this.entity, entity));
            }
            PowerHolderComponent.syncPower(this.entity, this.type);
        }
    }

    public void remove(Entity entity) {
        if(entity == null || entity.world.isClient) {
            return;
        }
        UUID uuid = entity.getUuid();
        remove(uuid);
    }

    public void remove(UUID uuid) {
        if(entityUuids.contains(uuid)) {
            Entity entity = getEntity(uuid);
            entityUuids.remove(uuid);
            entities.remove(uuid);
            if(actionOnRemove != null) {
                actionOnRemove.accept(new Pair<>(this.entity, entity));
            }
            PowerHolderComponent.syncPower(this.entity, this.type);
        }
    }

    public boolean contains(Entity entity) {
        return entities.containsValue(entity) || entityUuids.contains(entity.getUuid());
    }

    public int size() {
        return entityUuids.size();
    }

    public void clear() {
        for(UUID uuid : entityUuids) {
            if(actionOnRemove != null) {
                Entity entity = getEntity(uuid);
                actionOnRemove.accept(new Pair<>(this.entity, entity));
            }
        }
        entityUuids.clear();
        entities.clear();
    }

    public Set<UUID> getIterationSet() {
        return new HashSet<>(entityUuids);
    }

    public Entity getEntity(UUID uuid) {
        Entity entity = null;
        if(entities.containsKey(uuid)) {
            entity = entities.get(uuid);
        }
        if(entity == null || entity.isRemoved()) {
            entity = ((ServerWorld)this.entity.world).getEntity(uuid);
        }
        if(entity != null) {
            entities.put(uuid, entity);
        }
        return entity;
    }

    @Override
    public NbtElement toTag() {
        long[] uuidBits = new long[entityUuids.size() * 2];
        int index = 0;
        for(UUID uuid : entityUuids) {
            uuidBits[index++] = uuid.getLeastSignificantBits();
            uuidBits[index++] = uuid.getMostSignificantBits();
        }
        return new NbtLongArray(uuidBits);
    }

    @Override
    public void fromTag(NbtElement tag) {
        if(!(tag instanceof NbtLongArray array)) {
            return;
        }
        entityUuids.clear();
        entities.clear();
        for(int index = 0; index < array.size(); index += 2) {
            long least = array.get(index).longValue();
            long most = array.get(index + 1).longValue();
            entityUuids.add(new UUID(most, least));
        }
    }

    public static PowerFactory createFactory() {
        return new PowerFactory<>(Apoli.identifier("entity_set"),
                new SerializableData()
                        .add("action_on_add", ApoliDataTypes.BIENTITY_ACTION, null)
                        .add("action_on_remove", ApoliDataTypes.BIENTITY_ACTION, null),
                data ->
                        (type, player) -> new EntitySetPower(type, player,
                                data.get("action_on_add"),
                                data.get("action_on_remove")));
    }
}
