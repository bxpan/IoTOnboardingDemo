package com.amazon.aws.speedometer;

/**
 * Created by bxpan on 2/28/17.
 */

public class CruiseControl {
    public State state;

    CruiseControl() {
        state = new State();
    }

    public class State {
        Desired desired;
        Delta delta;

        State() {
            desired = new Desired();
            delta = new Delta();
        }

        public class Desired {
            Desired() {
            }

            public Integer speed;
            public Boolean isActivated;
        }

        public class Delta {
            Delta() {
            }

            public Integer speed;
            public Boolean isActivated;
        }
    }

    public Long version;
    public Long timestamp;
}
