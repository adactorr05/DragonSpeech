package com.dragonspeech.channel;

import com.dragonspeech.effect.EffectHandler;
import com.dragonspeech.effect.EffectInvocation;

import java.util.UUID;

public record ActiveChannel(
    UUID casterId,
    EffectHandler handler,
    EffectInvocation invocation,
    float costPerTick,
    long startGameTime
) {}
