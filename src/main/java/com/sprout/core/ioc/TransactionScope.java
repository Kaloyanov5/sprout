package com.sprout.core.ioc;

import java.util.logging.Logger;

public class TransactionScope {

    private static final Logger logger = Logger.getLogger(TransactionScope.class.getName());
    private boolean isActive;

    public TransactionScope() {
        this.isActive = false;
    }

    public void begin() {
        if (isActive) {
            logger.warning("--- TX is already active ---");
            return;
        }
        logger.info("--- BEGIN TX ---");
        isActive = true;
    }

    public void commit() {
        if (!isActive) {
            logger.warning("--- No active TX ---");
            return;
        }
        logger.info("--- COMMIT TX ---");
        isActive = false;
    }

    public void rollback() {
        if (!isActive) {
            logger.warning("--- No active TX ---");
            return;
        }
        logger.info("--- ROLLBACK TX ---");
        isActive = false;
    }

    public boolean isActive() {
        return isActive;
    }
}
