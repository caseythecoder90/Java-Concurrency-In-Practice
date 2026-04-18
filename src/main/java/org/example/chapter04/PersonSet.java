package org.example.chapter04;

import java.util.HashSet;
import java.util.Set;

/**
 * Listing 4.2 - Using Confinement to Ensure Thread Safety.
 *
 * The internal HashSet is NOT thread-safe — but PersonSet is.
 * Instance confinement + consistent locking makes the composite safe:
 *   - mySet is private and final.
 *   - The reference never escapes.
 *   - All access goes through synchronized methods (lock = `this`).
 *
 * If Person is itself mutable, callers still need to handle Person's
 * thread safety separately (e.g. make Person immutable or guard it).
 */
public class PersonSet {

    private final Set<Person> mySet = new HashSet<>();

    public synchronized void addPerson(Person p) {
        mySet.add(p);
    }

    public synchronized boolean containsPerson(Person p) {
        return mySet.contains(p);
    }

    public synchronized int size() {
        return mySet.size();
    }

    public static final class Person {
        private final String name;

        public Person(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Person p && p.name.equals(name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }
}
