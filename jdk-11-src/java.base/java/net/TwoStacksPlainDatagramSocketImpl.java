/*
 * Copyright (c) 2007, 2019, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */
package java.net;

import java.io.IOException;
import java.io.FileDescriptor;
import sun.net.ResourceManager;

/**
 * This class defines the plain DatagramSocketImpl that is used for all
 * Windows versions lower than Vista. It adds support for IPv6 on
 * these platforms where available.
 *
 * For backward compatibility windows platforms that do not have IPv6
 * support also use this implementation, and fd1 gets set to null
 * during socket creation.
 *
 * @author Chris Hegarty
 */

final class TwoStacksPlainDatagramSocketImpl extends AbstractPlainDatagramSocketImpl
{
    /* Used for IPv6 on Windows only */
    private FileDescriptor fd1;

    /*
     * Needed for ipv6 on windows because we need to know
     * if the socket was bound to ::0 or 0.0.0.0, when a caller
     * asks for it. In this case, both sockets are used, but we
     * don't know whether the caller requested ::0 or 0.0.0.0
     * and need to remember it here.
     */
    private InetAddress anyLocalBoundAddr=null;

    private int fduse=-1; /* saved between peek() and receive() calls */

    /* saved between successive calls to receive, if data is detected
     * on both sockets at same time. To ensure that one socket is not
     * starved, they rotate using this field
     */
    private int lastfd=-1;

    static {
        init();
    }

    // true if this socket is exclusively bound
    private final boolean exclusiveBind;

    /*
     * Set to true if SO_REUSEADDR is set after the socket is bound to
     * indicate SO_REUSEADDR is being emulated
     */
    private boolean reuseAddressEmulated;

    // emulates SO_REUSEADDR when exclusiveBind is true and socket is bound
    private boolean isReuseAddress;

    TwoStacksPlainDatagramSocketImpl(boolean exclBind) {
        exclusiveBind = exclBind;
    }

    protected synchronized void create() throws SocketException {
        fd1 = new FileDescriptor();
        try {
            super.create();
            // make SocketCleanable treat fd1 as a stream socket
            // to avoid touching the counter in ResourceManager
            SocketCleanable.register(fd1, true);
        } catch (SocketException e) {
            fd1 = null;
            throw e;
        }
    }

    protected synchronized void bind(int lport, InetAddress laddr)
        throws SocketException {
        super.bind(lport, laddr);
        if (laddr.isAnyLocalAddress()) {
            anyLocalBoundAddr = laddr;
        }
    }

    @Override
    protected synchronized void bind0(int lport, InetAddress laddr)
        throws SocketException
    {
        // The native bind0 may close one or both of the underlying file
        // descriptors, and even create new sockets, so the safest course of
        // action is to unregister the socket cleaners, and register afterwards.
        SocketCleanable.unregister(fd);
        SocketCleanable.unregister(fd1);

        bind0(lport, laddr, exclusiveBind);

        SocketCleanable.register(fd, false);
        // make SocketCleanable treat fd1 as a stream socket
        // to avoid touching the counter in ResourceManager
        SocketCleanable.register(fd1, true);
    }

    protected synchronized void receive(DatagramPacket p)
        throws IOException {
        try {
            receive0(p);
        } finally {
            fduse = -1;
        }
    }

    public Object getOption(int optID) throws SocketException {
        if (isClosed()) {
            throw new SocketException("Socket Closed");
        }

        if (optID == SO_BINDADDR) {
            if ((fd != null && fd1 != null) && !connected) {
                return anyLocalBoundAddr;
            }
            int family = connectedAddress == null ? -1 : connectedAddress.holder().getFamily();
            return socketLocalAddress(family);
        } else if (optID == SO_REUSEADDR && reuseAddressEmulated) {
            return isReuseAddress;
        } else if (optID == SO_REUSEPORT) {
            // SO_REUSEPORT is not supported on Windows.
            throw new UnsupportedOperationException("unsupported option");
        } else {
            return super.getOption(optID);
        }
    }

    protected void socketSetOption(int opt, Object val)
        throws SocketException
    {
        if (opt == SO_REUSEADDR && exclusiveBind && localPort != 0)  {
            // socket already bound, emulate
            reuseAddressEmulated = true;
            isReuseAddress = (Boolean)val;
        } else if (opt == SO_REUSEPORT) {
            // SO_REUSEPORT is not supported on Windows.
            throw new UnsupportedOperationException("unsupported option");
        } else {
            socketNativeSetOption(opt, val);
        }

    }

    protected boolean isClosed() {
        return (fd == null && fd1 == null) ? true : false;
    }

    protected void close() {
        if (fd != null || fd1 != null) {
            SocketCleanable.unregister(fd);
            SocketCleanable.unregister(fd1);
            datagramSocketClose();
            ResourceManager.afterUdpClose();
            fd = null;
            fd1 = null;
        }
    }

    /* Native methods */

    protected synchronized native void bind0(int lport, InetAddress laddr,
                                             boolean exclBind)
        throws SocketException;

    protected native void send0(DatagramPacket p) throws IOException;

    protected synchronized native int peek(InetAddress i) throws IOException;

    protected synchronized native int peekData(DatagramPacket p) throws IOException;

    protected synchronized native void receive0(DatagramPacket p)
        throws IOException;

    protected native void setTimeToLive(int ttl) throws IOException;

    protected native int getTimeToLive() throws IOException;

    @Deprecated
    protected native void setTTL(byte ttl) throws IOException;

    @Deprecated
    protected native byte getTTL() throws IOException;

    protected native void join(InetAddress inetaddr, NetworkInterface netIf)
        throws IOException;

    protected native void leave(InetAddress inetaddr, NetworkInterface netIf)
        throws IOException;

    protected native void datagramSocketCreate() throws SocketException;

    protected native void datagramSocketClose();

    protected native void socketNativeSetOption(int opt, Object val)
        throws SocketException;

    protected native Object socketGetOption(int opt) throws SocketException;

    protected native void connect0(InetAddress address, int port) throws SocketException;

    protected native Object socketLocalAddress(int family) throws SocketException;

    protected native void disconnect0(int family);

    native int dataAvailable();

    /**
     * Perform class load-time initializations.
     */
    private static native void init();
}
