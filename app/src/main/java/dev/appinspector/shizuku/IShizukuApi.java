/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: /home/hatch/workspace/7Z1H/tools/android-sdk/build-tools/35.0.0/aidl --lang=java -I /home/hatch/workspace/7Z1H/codes/app-inspector/app/src/main/aidl --out /tmp/aidl-out /home/hatch/workspace/7Z1H/codes/app-inspector/app/src/main/aidl/dev/appinspector/shizuku/IShizukuApi.aidl
 */
package dev.appinspector.shizuku;
/**
 * 运行在 Shizuku UserService 进程（shell 身份）中的接口。
 * 作用等价于 `adb shell pm ...`，用于免 Root 执行停用/删除操作。
 */
public interface IShizukuApi extends android.os.IInterface
{
  /** Default implementation for IShizukuApi. */
  public static class Default implements dev.appinspector.shizuku.IShizukuApi
  {
    /**
     * 以 shell 身份执行 pm 命令。
     * @param args pm 参数，例如 ["disable-user", "--user", "0", "com.example"]
     * @return pm 的完整输出（stdout+stderr）；包含 "Failure" 即执行失败
     */
    @Override public java.lang.String execPm(java.lang.String[] args) throws android.os.RemoteException
    {
      return null;
    }
    /** 退出 service 进程。调用后 binder 失效，须在最后调用。 */
    @Override public void destroy() throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements dev.appinspector.shizuku.IShizukuApi
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an dev.appinspector.shizuku.IShizukuApi interface,
     * generating a proxy if needed.
     */
    public static dev.appinspector.shizuku.IShizukuApi asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof dev.appinspector.shizuku.IShizukuApi))) {
        return ((dev.appinspector.shizuku.IShizukuApi)iin);
      }
      return new dev.appinspector.shizuku.IShizukuApi.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      if (code == INTERFACE_TRANSACTION) {
        reply.writeString(descriptor);
        return true;
      }
      switch (code)
      {
        case TRANSACTION_execPm:
        {
          java.lang.String[] _arg0;
          _arg0 = data.createStringArray();
          java.lang.String _result = this.execPm(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_destroy:
        {
          this.destroy();
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements dev.appinspector.shizuku.IShizukuApi
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      /**
       * 以 shell 身份执行 pm 命令。
       * @param args pm 参数，例如 ["disable-user", "--user", "0", "com.example"]
       * @return pm 的完整输出（stdout+stderr）；包含 "Failure" 即执行失败
       */
      @Override public java.lang.String execPm(java.lang.String[] args) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStringArray(args);
          boolean _status = mRemote.transact(Stub.TRANSACTION_execPm, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /** 退出 service 进程。调用后 binder 失效，须在最后调用。 */
      @Override public void destroy() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_destroy, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_execPm = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_destroy = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "dev.appinspector.shizuku.IShizukuApi";
  /**
   * 以 shell 身份执行 pm 命令。
   * @param args pm 参数，例如 ["disable-user", "--user", "0", "com.example"]
   * @return pm 的完整输出（stdout+stderr）；包含 "Failure" 即执行失败
   */
  public java.lang.String execPm(java.lang.String[] args) throws android.os.RemoteException;
  /** 退出 service 进程。调用后 binder 失效，须在最后调用。 */
  public void destroy() throws android.os.RemoteException;
}
