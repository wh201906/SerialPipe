package io.github.wh201906.serialpipe;

public abstract class BaseConnection implements Connection
{
    protected boolean mIsOpened = false;
    protected Exception mLastException = null;

    @Override
    public Exception getLastException() {return mLastException;}

    @Override
    public boolean isOpened() {return mIsOpened;}
}
