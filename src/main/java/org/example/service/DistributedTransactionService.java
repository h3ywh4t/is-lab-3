package org.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Service
public class DistributedTransactionService {

    private static final Logger logger = LoggerFactory.getLogger(DistributedTransactionService.class);

    @Autowired
    private MinIOService minIOService;

    public static class TransactionPhase {
        private String name;
        private Supplier<Object> prepareAction;
        private Runnable commitAction;
        private Runnable rollbackAction;
        private Object preparedResult;
        private boolean prepared = false;
        private boolean committed = false;

        public TransactionPhase(String name, Supplier<Object> prepareAction, Runnable commitAction, Runnable rollbackAction) {
            this.name = name;
            this.prepareAction = prepareAction;
            this.commitAction = commitAction;
            this.rollbackAction = rollbackAction;
        }

        public Object prepare() throws Exception {
            if (prepared) {
                return preparedResult;
            }
            logger.info("Preparing phase: {}", name);
            preparedResult = prepareAction.get();
            prepared = true;
            logger.info("Phase {} prepared successfully", name);
            return preparedResult;
        }

        public void commit() {
            if (committed) {
                return;
            }
            logger.info("Committing phase: {}", name);
            commitAction.run();
            committed = true;
            logger.info("Phase {} committed successfully", name);
        }

        public void rollback() {
            if (!prepared) {
                return;
            }
            logger.warn("Rolling back phase: {}", name);
            try {
                rollbackAction.run();
            } catch (Exception e) {
                logger.error("Error during rollback of phase {}: {}", name, e.getMessage());
            }
            logger.info("Phase {} rolled back", name);
        }

        public boolean isPrepared() {
            return prepared;
        }

        public boolean isCommitted() {
            return committed;
        }
    }

    public <T> T executeDistributedTransaction(
            List<TransactionPhase> phases,
            Supplier<T> businessLogic) throws Exception {

        List<TransactionPhase> preparedPhases = new ArrayList<>();
        
        try {
            logger.info("Phase 1");
            for (TransactionPhase phase : phases) {
                try {
                    phase.prepare();
                    preparedPhases.add(phase);
                } catch (Exception e) {
                    logger.error("Phase {} failed during prepare: {}", phase.name, e.getMessage());
                    rollbackAll(preparedPhases);
                    throw new RuntimeException("Transaction prepare failed for phase " + phase.name + ": " + e.getMessage(), e);
                }
            }

            logger.info("Executing business logic");
            T result = null;
            try {
                result = businessLogic.get();
            } catch (RuntimeException e) {
                logger.error("Business logic failed: {}", e.getMessage());
                rollbackAll(preparedPhases);
                throw e;
            }

            logger.info("Phase 2");
            for (TransactionPhase phase : preparedPhases) {
                try {
                    phase.commit();
                } catch (Exception e) {
                    logger.error("Phase {} failed during commit: {}", phase.name, e.getMessage());
                    rollbackAll(preparedPhases);
                    throw new RuntimeException("Transaction commit failed for phase " + phase.name + ": " + e.getMessage(), e);
                }
            }

            logger.info("Distributed transaction completed successfully");
            return result;

        } catch (Exception e) {
            logger.error("Distributed transaction failed");
            rollbackAll(preparedPhases);
            throw e;
        }
    }

    private void rollbackAll(List<TransactionPhase> phases) {
        logger.info("Rolling back all prepared phases");
        for (int i = phases.size() - 1; i >= 0; i--) {
            TransactionPhase phase = phases.get(i);
            if (phase.isPrepared() && !phase.isCommitted()) {
                phase.rollback();
            }
        }
    }

    public TransactionPhase createMinIOPhase(String objectName, MultipartFile file) {
        return new TransactionPhase(
                "MinIO Upload: " + objectName,
                () -> {
                    try {
                        return minIOService.uploadFile(file, objectName);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to upload file to MinIO: " + e.getMessage(), e);
                    }
                },
                () -> {
                    logger.debug("MinIO file {} committed", objectName);
                },
                () -> {
                    try {
                        if (minIOService.fileExists(objectName)) {
                            minIOService.deleteFile(objectName);
                            logger.info("MinIO file {} deleted during rollback", objectName);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to delete MinIO file {} during rollback: {}", objectName, e.getMessage());
                        throw new RuntimeException("Failed to rollback MinIO file: " + objectName, e);
                    }
                }
        );
    }
}