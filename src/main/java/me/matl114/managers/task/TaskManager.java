package me.matl114.managers.task;

import com.google.common.base.Preconditions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public interface TaskManager {
    public static TaskManager of(){
        final HashMap<String ,Runnable> toggles = new LinkedHashMap<>();
        return new TaskManagerImpl(toggles);
    }
    public static TaskManager of(HashMap<String,Runnable> map){
        return new TaskManagerImpl(map);
    }

    @Nullable
    public Runnable getTask(String value);
    @Nonnull
    public Map<String ,Runnable> getTasks();
    public void register(String value,Runnable task);
    @Nonnull
    public Runnable getOrRegister(String value, Runnable task);



    public static class TaskManagerImpl implements TaskManager {
        private final Map<String ,Runnable> tasks ;
        public TaskManagerImpl(Map<String ,Runnable> tasks){
            this.tasks = tasks;
        }
        public TaskManagerImpl(){
            this.tasks = new LinkedHashMap<>();
        }
        public Runnable getTask(String value){
            //Preconditions.checkArgument(tasks.containsKey(value));
            return tasks.get(value);
        }

        public Map<String ,Runnable> getTasks(){

            return Collections.unmodifiableMap(this.tasks);

        }

        public void register(String value,Runnable task){
            //Preconditions.checkArgument(!tasks.containsKey(value));
            tasks.put(value, task);
        }

        @Override
        public Runnable getOrRegister(String value, Runnable task) {
            return this.tasks.computeIfAbsent(value, (r) -> task);
        }

    }
}
