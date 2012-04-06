package de.diddiz.LogBlock.listeners;

import org.bukkit.event.Listener;

import de.diddiz.LogBlock.*;



public class LoggingListener implements Listener {
    protected final Consumer consumer;

    public LoggingListener(final LogBlock lb) {
        this.consumer = lb.getConsumer();
    }
}
