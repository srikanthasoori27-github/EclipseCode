/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.reports;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.rapidapponboarding.logger.LogEnablement;
/**
 * This is a generic class that is developed to create a pool of threads(either
 * cached or fixed)<br>
 * This class uses the passed in list of callable objects to create a future
 * task for each object<br>
 * This class times out future tasks based on passed in parameter timeOutWindow<br>
 * All future tasks are processed and computed by using the thread pool<br>
 * A future task will be cancelled in case it doesn't get response in the
 * specified time frame "timeOutWindow"<br>
 * 
 * @author Rohit Gupta
 * 
 */
public class ThreadPoolTasksProcessor {
	private int numberOfThreads;
	private int timeOutWindow;
	private Collection<Callable<Map<String, Object>>> callableList;
	private static final Log threadPoolLogger = LogFactory
			.getLog(ThreadPoolTasksProcessor.class);
	private List<Map<String, Object>> results;
	private int success = 0;
	private int failure = 0;
	private int totalTimeOuts = 0;
	ExecutorService executor = null;
	ExecutorCompletionService completionService = null;
	private boolean newCachedThreadPool = false;
	/**
	 * Default Constructor
	 * 
	 * @param numberOfThreads
	 *            The total number of threads for the pool
	 * @param timeOutWindow
	 *            The time out window in ms for future tasks
	 * @param callableList
	 *            The list of callable objects
	 * @param results
	 *            The result map to keep track of the future task result
	 * @param newCachedThreadPool
	 *            Parameter to create cached thread pool
	 */
	public ThreadPoolTasksProcessor(int numberOfThreads, int timeOutWindow,
			List<Callable<Map<String, Object>>> callableList,
			List<Map<String, Object>> results, boolean newCachedThreadPool) {
		this.numberOfThreads = numberOfThreads;
		this.timeOutWindow = timeOutWindow;
		this.callableList = callableList;
		this.results = results;
		this.newCachedThreadPool = newCachedThreadPool;
	}
	/**
	 * This method starts the processing of Future tasks using thread pool
	 * created from Executors class
	 */
	public void start() {
		List<Future<Map<String, Object>>> finalListFuture = new ArrayList();
		LogEnablement.isLogDebugEnabled(threadPoolLogger,
"TOTAL TASKS THREADS " + numberOfThreads);
		LogEnablement.isLogDebugEnabled(threadPoolLogger,
"TOTAL TASKS TIME OUT WINDOW " + timeOutWindow);
		if (newCachedThreadPool) {
			executor = Executors.newCachedThreadPool();
		} else {
			executor = Executors.newFixedThreadPool(numberOfThreads);
		}
		completionService = new ExecutorCompletionService(executor);
		long startTime = 0;
		try {
			List<Future<Map<String, Object>>> listFutureResult = null;
			startTime = new Date().getTime();
			LogEnablement.isLogDebugEnabled(threadPoolLogger,
"TOTAL TASKS INVOCATION ->" + callableList.size());
			for (Callable<Map<String, Object>> call : callableList) {
				Future future = (Future<Map<String, Object>>) completionService
						.submit(call);
				finalListFuture.add(future);
			}
			LogEnablement.isLogDebugEnabled(threadPoolLogger,
"TOTAL TASKS INVOCATION FINISHED ->"
					+ callableList.size());
			if (finalListFuture != null) {
				LogEnablement.isLogDebugEnabled(threadPoolLogger,
"ALL CONCURRENT TASKS RESULTS IN ARRAY IN SIZE "
						+ finalListFuture.toArray().length);
				LogEnablement.isLogDebugEnabled(threadPoolLogger,
"ALL CONCURRENT TASKS RESULTS IN ARRAY"
						+ finalListFuture.toArray());
				calculateResults(finalListFuture);
			}
		} catch (Exception ex) {
			LogEnablement.isLogErrorEnabled(threadPoolLogger,"Exception from SSThreadPoolTasksProcessor "
					+ ex.getMessage());
		} finally {
			LogEnablement.isLogDebugEnabled(threadPoolLogger,
"TOTAL SUCCESS - " + success);
			LogEnablement.isLogDebugEnabled(threadPoolLogger,
"TOTAL FAILURE - " + failure);
			LogEnablement.isLogDebugEnabled(threadPoolLogger,
"TOTAL TIME - " + (new Date().getTime() - startTime)
					/ 1000 + "SECONDS");
			if (executor != null) {
				executor.shutdown();
			}
			for (Future future : finalListFuture) {
				future.cancel(true);
			}
		}
	}
	/**
	 * This methods calculates the result of each future task<br>
	 * This method is recursive and gets called on TimeOutException<br>
	 * It stores the result of each Future Task in class List variable "result"
	 * 
	 * @param originalListFuture
	 *            The list of Future Objects
	 */
	public void calculateResults(
			List<Future<Map<String, Object>>> originalListFuture) {
		LogEnablement.isLogDebugEnabled(threadPoolLogger,
"originalListFuture " + originalListFuture);
		Future timeOutFuture = null;
		Future completedFuture = null;
		Future cancelledFuture = null;
		List<Future<Map<String, Object>>> timeOutCancelledCompletedListFuture = new ArrayList();
		timeOutCancelledCompletedListFuture = originalListFuture;
		List<Future<Map<String, Object>>> completedListFuture = new ArrayList();
		List<Future<Map<String, Object>>> cancelledListFuture = new ArrayList();
		try {
			if (originalListFuture != null && originalListFuture.size() > 0) {
				for (Future<Map<String, Object>> future : originalListFuture) {
					timeOutFuture = future;
					LogEnablement.isLogDebugEnabled(threadPoolLogger,
"future: " + future);
					Map<String, Object> map = future.get(timeOutWindow,
							TimeUnit.MILLISECONDS);
					if (future.isDone()) 
					{
						completedFuture = future;
						timeOutFuture = null;
						LogEnablement.isLogDebugEnabled(threadPoolLogger,
"Worker Completed: " + map);
						results.add(map);
						success = success + 1;
						if (completedListFuture != null
								&& completedFuture != null) {
							LogEnablement.isLogDebugEnabled(threadPoolLogger,
" Completed Future ->" + completedFuture);
							completedListFuture.add(completedFuture);
						}
					} 
					else if (future.isCancelled()) 
					{
						cancelledFuture = future;
						timeOutFuture = null;
						LogEnablement.isLogDebugEnabled(threadPoolLogger,
"Worker Cancelled: " + map);
						failure = failure + 1;
						if (cancelledListFuture != null
								&& cancelledFuture != null) {
							LogEnablement.isLogDebugEnabled(threadPoolLogger,
" Cancelled Future " + cancelledFuture);
							cancelledListFuture.add(cancelledFuture);
						}
					}
				}
			}
		}
		catch (CancellationException cancellationException) {
			LogEnablement.isLogErrorEnabled(threadPoolLogger,"CancellationException from SSThreadPoolTasksProcessor "
					+ cancellationException.getMessage());
			failure = failure + 1;
		} catch (ExecutionException executionException) {
			LogEnablement.isLogErrorEnabled(threadPoolLogger,"ExecutionException from SSThreadPoolTasksProcessor "
					+ executionException.getMessage());
			failure = failure + 1;
		} catch (InterruptedException interruptedException) {
			LogEnablement.isLogErrorEnabled(threadPoolLogger,"InterruptedException from SSThreadPoolTasksProcessor "
					+ interruptedException.getMessage());
			failure = failure + 1;
		} catch (TimeoutException timeOutException) {
			LogEnablement.isLogErrorEnabled(threadPoolLogger,"TimeOutException from SSThreadPoolTasksProcessor "
					+ timeOutException.getMessage());
			LogEnablement.isLogDebugEnabled(threadPoolLogger,
"Worker Timed out: ");
			boolean interruptExecutionOfThread = false;
			if (timeOutFuture != null) {
				interruptExecutionOfThread = timeOutFuture.cancel(true);
			}
			LogEnablement.isLogDebugEnabled(threadPoolLogger,
"Cancel timed out future task ->" + timeOutFuture
					+ ":Outcome of cancellation is:"
					+ interruptExecutionOfThread);
			failure = failure + 1;
			totalTimeOuts = totalTimeOuts + 1;
			LogEnablement.isLogDebugEnabled(threadPoolLogger,
"Total Time Outs->" + totalTimeOuts);
			/*
			 * LogEnablement.isLogDebugEnabled(threadPoolLogger,
"Total Threads->"+numberOfThreads);
			 * if(totalTimeOuts>=numberOfThreads && numberOfThreads>=3) {
			 * threadPoolLogger.debug
			 * ("We need to bump the timeoutWindow from->"+timeOutWindow);
			 * timeOutWindow=timeOutWindow+60000;
			 * LogEnablement.isLogDebugEnabled(threadPoolLogger,
"We need to bump the timeoutWindow to->"
			 * +timeOutWindow); }
			 */
			if (completedListFuture != null && completedListFuture.size() > 0
					&& timeOutCancelledCompletedListFuture != null
					&& timeOutCancelledCompletedListFuture.size() > 0) {
				for (Future compFuture : completedListFuture) {
					LogEnablement.isLogDebugEnabled(threadPoolLogger,
"Before Remove completed future from master  ->"
							+ timeOutCancelledCompletedListFuture.size());
					if (timeOutCancelledCompletedListFuture
							.contains(compFuture)) {
						timeOutCancelledCompletedListFuture.remove(compFuture);
					}
					LogEnablement.isLogDebugEnabled(threadPoolLogger,
"After Remove completed future from master ->"
							+ timeOutCancelledCompletedListFuture.size());
				}
			}
			if (cancelledListFuture != null && cancelledListFuture.size() > 0
					&& cancelledFuture != null
					&& timeOutCancelledCompletedListFuture != null
					&& timeOutCancelledCompletedListFuture.size() > 0) {
				for (Future canFuture : cancelledListFuture) {
					LogEnablement.isLogDebugEnabled(threadPoolLogger,
"Before Remove cancelled future from master  ->"
							+ timeOutCancelledCompletedListFuture.size());
					if (timeOutCancelledCompletedListFuture.contains(canFuture)) {
						timeOutCancelledCompletedListFuture.remove(canFuture);
					}
					LogEnablement.isLogDebugEnabled(threadPoolLogger,
"After Remove cancelled future from master ->"
							+ timeOutCancelledCompletedListFuture.size());
				}
			}
			if (timeOutCancelledCompletedListFuture != null
					&& timeOutCancelledCompletedListFuture.size() > 0
					&& timeOutFuture != null
					&& timeOutCancelledCompletedListFuture
							.contains(timeOutFuture)) {
				LogEnablement.isLogDebugEnabled(threadPoolLogger,
"About to Start Recursion ThreadPoolTasksProcessor timeout list ->"
						+ timeOutCancelledCompletedListFuture.size());
				timeOutCancelledCompletedListFuture.remove(timeOutFuture);
				LogEnablement.isLogDebugEnabled(threadPoolLogger,
"Start Recursion ThreadPoolTasksProcessor after future removal ->"
						+ timeOutCancelledCompletedListFuture.size());
				calculateResults(timeOutCancelledCompletedListFuture);
			} else {
				LogEnablement.isLogDebugEnabled(threadPoolLogger,
"Finished Recursion ThreadPoolTasksProcessor ");
			}
		}
		catch (Exception exception) {
			LogEnablement.isLogErrorEnabled(threadPoolLogger,"Exception from ThreadPoolTasksProcessor "
					+ exception.getMessage());
			failure = failure + 1;
		}
	}
}
