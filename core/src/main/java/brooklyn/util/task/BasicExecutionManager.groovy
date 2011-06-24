package brooklyn.util.task;

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

import brooklyn.management.ExecutionManager
import brooklyn.management.Task


public class BasicExecutionManager implements ExecutionManager {
	
	private static class PerThreadCurrentTaskHolder {
		public static final perThreadCurrentTask = new ThreadLocal<Task>();
	}
	public static ThreadLocal<Task> getPerThreadCurrentTask() {
		return PerThreadCurrentTaskHolder.perThreadCurrentTask;
	}
	
	public static Task getCurrentTask() { return getPerThreadCurrentTask().get() }
	
	private ExecutorService runner = Executors.newCachedThreadPool() 
	
	private Set<Task> knownTasks = new LinkedHashSet()
	private Map<Object,Set<Task>> tasksByTag = new LinkedHashMap()
	//access to the above is synchronized in code in this class, to allow us to preserve order while guaranteeing thread-safe
	//(but more testing is needed before we are sure it is thread-safe!)
	//synch blocks are as finely grained as possible for efficiency
	
	public Set<Task> getTasksWithTag(Object tag) {
		Set<Task> tasksWithTag;
		synchronized (tasksByTag) {
			tasksWithTag = tasksByTag.get(tag)
		}
		if (tasksWithTag==null) return Collections.emptySet()
		synchronized (tasksWithTag) {
			return new LinkedHashSet(tasksWithTag)
		} 
	}
	public Set<Task> getTasksWithAnyTag(Iterable tags) {
		Set result = []
		tags.each { tag -> result.addAll( getTasksWithTag(tag) ) }
		result
	}
	public Set<Task> getTasksWithAllTags(Iterable tags) {
		//NB: for this method retrieval for multiple tags could be made (much) more efficient (if/when it is used with multiple tags!)
		//by first looking for the least-used tag, getting those tasks, and then for each of those tasks 
		//checking whether it contains the other tags (looking for second-least used, then third-least used, etc)
		Set result = null
		tags.each {
			tag ->
			if (result==null) result = getTasksWithTag(tag)
			else {
				result.retainAll getTasksWithTag(tag)
				if (!result) return result  //abort if we are already empty
			} 
		}
		result
	}
	public Set<Object> getTaskTags() { synchronized (tasksByTag) { return new LinkedHashSet(tasksByTag.keySet()) }}
	public Set<Task> getAllTasks() { synchronized (knownTasks) { return new LinkedHashSet(knownTasks) }}
	
	public Task submit(Map flags=[:], Runnable r) { submit flags, new BasicTask(r) }
	public Task submit(Map flags=[:], Callable r) { submit flags, new BasicTask(r) }
	public Task submit(Map flags=[:], Task task) {
		if (task.result!=null) return task
		synchronized (task) {
			if (task.result!=null) return task
			submitNewTask flags, task
		}
	}

	protected Task submitNewTask(Map flags, Task task) {
		beforeSubmit(flags, task)
		Closure job = { 
			Object result = null
			try { 
				beforeStart(flags, task);
				result = task.job.call() 
			} finally { 
				afterEnd(flags, task) 
			}
			result
		}
		task.initExecutionManager(this)
		// 'as Callable' to prevent being treated as Runnable and returning a future that gives null
		task.initResult(runner.submit(job as Callable))
		task
	}

	protected void beforeSubmit(Map flags, Task task) {
		task.submittedByTask = getCurrentTask()
		task.submitTimeUtc = System.currentTimeMillis()
		synchronized (knownTasks) {
			knownTasks << task
		}
		if (flags.tag) task.@tags.add flags.remove("tag")
		if (flags.tags) task.@tags.addAll flags.remove("tags")

		List tagBuckets = []
		synchronized (tasksByTag) {
			task.@tags.each { tag ->
				Set tagBucket = tasksByTag.get tag
				if (tagBucket==null) {
					tagBucket = new LinkedHashSet()
					tasksByTag.put tag, tagBucket
				}
				tagBuckets.add tagBucket
			}
		}
		tagBuckets.each { bucket ->
			synchronized (bucket) {
				bucket << task
			}
		}
	}	
	protected void beforeStart(Map flags, Task task) {
		task.startTimeUtc = System.currentTimeMillis()
		perThreadCurrentTask.set task
		task.thread = Thread.currentThread()
		ExecutionUtils.invoke flags.newTaskStartCallback, task
	}

	protected void afterEnd(Map flags, Task task) {
		ExecutionUtils.invoke flags.newTaskEndCallback, task
		perThreadCurrentTask.remove()
		task.endTimeUtc = System.currentTimeMillis()
		//clear thread _after_ endTime set, so we won't get a null thread when there is no end-time
		task.thread = null;
		synchronized (task) { task.notifyAll() }
	}

}
