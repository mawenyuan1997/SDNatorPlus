package utils;

import java.util.Set;

public class Group {
    private String mode;
    private Set<String> members;

    public Group(Set<String> members) {
        this.members = members;
    }

    public Set<String> getMembers() {
        return members;
    }

    public int size() { return members.size(); }
}
