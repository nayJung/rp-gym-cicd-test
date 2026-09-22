package com.workoutdone.rpgym.game.party.application;

import com.workoutdone.rpgym.common.exception.BaseException;

public class PartyException extends BaseException {
    public PartyException(PartyErrorCode errorCode){
        super(errorCode);
    }
}
