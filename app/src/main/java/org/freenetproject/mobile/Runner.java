package org.freenetproject.mobile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Responsible for starting, stopping, pausing and resuming the node, as well as
 * reporting whether the node is running. Bridges the app's Manager to the
 * freenet-mobile node-wrapper's NodeController.
 */
public class Runner {
    /** Timeout to wait for the FCP server to be reachable after starting the node. */
    private static final long STARTUP_TIMEOUT = 60_000L;
    /** Poll interval while waiting for the FCP server. */
    private static final long STARTUP_POLL_INTERVAL = 500L;

    private static Runner instance = null;

    /** NodeController of the node this Runner controls, null until start() is called. */
    private NodeController nodeController = null;

    public static Runner getInstance() {
        if (instance == null) {
            instance = new Runner();
        }
        return instance;
    }

    /**
     * @return Whether this Runner controls a node and the node is currently running.
     */
    public boolean isStarted() {
        return nodeController != null && nodeController.isRunning();
    }

    /**
     * Starts the node with the given arguments (only the first argument, the path to
     * the freenet configuration file, is used).
     *
     * @param args List of arguments, starting with the path to the node configuration file.
     * @return 0 on success, -1 when the node was started before, not 0 when an error happened.
     */
    public int start(String[] args) {
        synchronized (this) {
            if (isStarted()) {
                return -1;
            }

            Path iniPath = Paths.get(args[0]);

            if (nodeController == null) {
                try {
                    // Creates a default configuration if the node directory does not
                    // have a freenet.ini yet.
                    nodeController = new NodeControllerImpl(iniPath.getParent());
                } catch (Exception e) {
                    return -2;
                }
            }

            try {
                nodeController.start();
            } catch (Exception e) {
                return -2;
            }
        }

        // The node spawns its FCP server asynchronously, wait until the node answers
        // on the FCP port or the startup times out.
        long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT;
        while (System.currentTimeMillis() < deadline) {
            if (nodeController.isRunning()) {
                return 0;
            }
            try {
                Thread.sleep(STARTUP_POLL_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -3;
            }
        }
        return -3;
    }

    /**
     * Stops the node.
     *
     * @return 0 on success, not 0 when an error happened.
     */
    public int stop() {
        if (!isStarted()) {
            return 0;
        }
        try {
            nodeController.shutdown();
            return 0;
        } catch (Exception e) {
            return -1;
        } finally {
            nodeController = null;
        }
    }

    /**
     * Pauses the node, which disables opennet communications.
     *
     * @return 0 on success, not 0 when an error happened.
     */
    public int pause() {
        if (!isStarted()) {
            return -1;
        }
        try {
            nodeController.pause();
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Resumes the node, which re-enables opennet communications.
     *
     * @return 0 on success, not 0 when an error happened.
     */
    public int resume() {
        if (!isStarted()) {
            return -1;
        }
        try {
            nodeController.resume();
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }
}
