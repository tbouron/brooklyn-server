package brooklyn.util.task;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import brooklyn.management.Task;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.collections.MutableMap;

public class TaskBuilder<T> {

    String name = null;
    Callable<T> body = null;
    List<Task<?>> children = null;
    boolean dynamic = false;
    boolean parallel = false;
    
    public static <T> TaskBuilder<T> builder() {
        return new TaskBuilder<T>();
    }
    
    public TaskBuilder<T> name(String name) {
        this.name = name;
        return this;
    }
    
    public TaskBuilder<T> dynamic(boolean dynamic) {
        this.dynamic = dynamic;
        return this;
    }
    
    public TaskBuilder<T> parallel(boolean parallel) {
        this.parallel = parallel;
        return this;
    }
    
    public TaskBuilder<T> body(Callable<T> body) {
        this.body = body;
        return this;
    }
    
    public TaskBuilder<T> body(Runnable body) {
        this.body = GroovyJavaMethods.<T>callableFromRunnable(body);
        return this;
    }

    public TaskBuilder<T> add(Task<?> child) {
        if (children==null) children = new ArrayList<Task<?>>();
        children.add(child);
        return this;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Task<T> build() {
        if (!dynamic && (children==null || children.isEmpty()))
            return new BasicTask<T>(MutableMap.of("name", name), body);
        
        if (dynamic) {
            if (parallel)
                throw new UnsupportedOperationException("No implementation of parallel dynamic aggregate task available");
            DynamicSequentialTask<T> result = new DynamicSequentialTask<T>(MutableMap.of("name", name), body);
            if (children!=null)
                for (Task t: children)
                    result.addTask(t);
            return result;
        }
        
        if (children==null) children = new ArrayList<Task<?>>();
        // T must be of type List<V> for these to be valid
        if (parallel)
            return new ParallelTask(MutableMap.of("name", name), children);
        else
            return new SequentialTask(MutableMap.of("name", name), children);
    }
    
}
