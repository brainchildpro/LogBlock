package de.diddiz.LogBlock;

import java.util.*;

class QuestionsReaper implements Runnable {
    private final Vector<Question> questions;

    public QuestionsReaper(final Vector<Question> questions) {
        this.questions = questions;
    }

    @Override
    public void run() {
        final Enumeration<Question> enm = this.questions.elements();
        while (enm.hasMoreElements()) {
            final Question question = enm.nextElement();
            if (question.isExpired()) this.questions.remove(question);
        }
    }
}
