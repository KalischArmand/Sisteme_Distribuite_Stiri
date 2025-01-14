package com.example.news.node.bully;

import com.example.news.node.bully.NodeProcess;

import java.util.List;

/**
 * Implementare completă a algoritmului Bully.
 */
public class BullyAlgorithm {

    /**
     * Declanșăm un proces de alegeri pornind de la nodul curent (self).
     * @param self NodeProcess care conține ID, listă noduri, funcții de trimitere mesaje etc.
     */
    public static void startElection(NodeProcess self) {
        System.out.println("[BullyAlgorithm] Node " + self.getId() + " => Starting ELECTION...");

        // 1. Trimite "ELECTION" la toate nodurile cu ID mai mare
        boolean anyHigherNodeResponded = false;
        List<Integer> higherNodes = self.getNodesWithIdHigher();
        for (int id : higherNodes) {
            boolean sentOk = self.sendElectionMessageTo(id);
            if (sentOk) {
                anyHigherNodeResponded = true;
            }
        }

        // 2. Dacă nu a răspuns niciun nod cu ID mai mare => devii coordonator
        if(!anyHigherNodeResponded) {
            becomeCoordinator(self);
        } else {
            // Așteptăm un timp să vedem dacă alt nod cu ID mai mare devine coordonator
            // sau continuă totuși
            waitForCoordinator(self);
        }
    }

    /**
     * Nodul curent devine coordonator și trimite "COORDINATOR" tuturor nodurilor cu ID mai mic.
     */
    public static void becomeCoordinator(NodeProcess self) {
        System.out.println("[BullyAlgorithm] Node " + self.getId() + " => I become COORDINATOR!");
        self.setCoordinator(self.getId());
        // Trimite COORDINATOR la toate nodurile
        self.announceCoordinatorToAll();
    }

    /**
     * Așteaptă un mesaj COORDINATOR de la alt nod cu ID mai mare (care și el a pornit o alegere).
     */
    private static void waitForCoordinator(NodeProcess self) {
        // Așteptăm un timp (ex: 3-5 sec) să primim COORDINATOR
        System.out.println("[BullyAlgorithm] Node " + self.getId() + " => waiting for coordinator message...");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if(self.getCoordinator() < 0) {
            // Nu a venit niciun COORDINATOR => devin eu
            becomeCoordinator(self);
        } else {
            System.out.println("[BullyAlgorithm] Node " + self.getId() + " => Coordinator is Node " + self.getCoordinator());
        }
    }
}
