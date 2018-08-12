package com.forcetower.sagres.executor;

import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SagresTaskExecutor extends TaskExecutor {
    private static volatile SagresTaskExecutor sInstance;

    @NonNull
    private TaskExecutor mDelegate;

    @NonNull
    private TaskExecutor mDefaultTaskExecutor;

    @NonNull
    private static final Executor sMainThreadExecutor = command -> getInstance().postToMainThread(command);

    @NonNull
    private static final Executor sIOThreadExecutor = command -> getInstance().executeOnDiskIO(command);

    @NonNull
    private static final Executor sNetworkThreadExecutor = command -> getInstance().executeOnNetworkIO(command);

    private SagresTaskExecutor() {
        mDefaultTaskExecutor = new DefaultTaskExecutor();
        mDelegate = mDefaultTaskExecutor;
    }

    /**
     * Returns an instance of the task executor.
     *
     * @return The singleton SagresTaskExecutor.
     */
    @NonNull
    public static SagresTaskExecutor getInstance() {
        if (sInstance != null) {
            return sInstance;
        }
        synchronized (SagresTaskExecutor.class) {
            if (sInstance == null) {
                sInstance = new SagresTaskExecutor();
            }
        }
        return sInstance;
    }

    /**
     * Sets a delegate to handle task execution requests.
     * <p>
     * If you have a common executor, you can set it as the delegate and App Toolkit components will
     * use your executors. You may also want to use this for your tests.
     * <p>
     * Calling this method with {@code null} sets it to the default TaskExecutor.
     *
     * @param taskExecutor The task executor to handle task requests.
     */
    public void setDelegate(@Nullable TaskExecutor taskExecutor) {
        mDelegate = taskExecutor == null ? mDefaultTaskExecutor : taskExecutor;
    }

    @Override
    public void executeOnDiskIO(@NonNull Runnable runnable) {
        mDelegate.executeOnDiskIO(runnable);
    }

    @Override
    public void executeOnNetworkIO(@NonNull Runnable runnable) {
        mDelegate.executeOnNetworkIO(runnable);
    }

    @Override
    public void postToMainThread(@NonNull Runnable runnable) {
        mDelegate.postToMainThread(runnable);
    }

    @NonNull
    public static Executor getMainThreadExecutor() {
        return sMainThreadExecutor;
    }

    @NonNull
    public static Executor getIOThreadExecutor() {
        return sIOThreadExecutor;
    }

    @NonNull
    public static Executor getNetworkThreadExecutor() {
        return sNetworkThreadExecutor;
    }

    @Override
    public boolean isMainThread() {
        return mDelegate.isMainThread();
    }
}