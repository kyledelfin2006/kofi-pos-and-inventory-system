package com.cc104.pos.model;

/** Local login credentials; the password is stored as salt and hash. */
public record User(String username, String salt, String passwordHash) { }
