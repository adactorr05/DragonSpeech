package com.dragonspeech.mob.casting;
import com.dragonspeech.ward.WardType;
import java.util.Map;
public interface Warded { Map<WardType,MobWards.WardInstance> activeWards(); }
