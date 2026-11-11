package com.cc104.pos.model;

import java.time.LocalDateTime;

/** A short dashboard entry describing a recent store action. */
public record Activity(LocalDateTime occurredAt, String action, String detail) { }
