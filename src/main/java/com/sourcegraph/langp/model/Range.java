package com.sourcegraph.langp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

public class Range extends RepoRev {

    @JsonProperty(value="File")
    private String file;

    @JsonProperty(value="StartLine")
    private int startLine;

    @JsonProperty(value="StartCharacter")
    private int startCharacter;

    @JsonProperty(value="EndLine")
    private int endLine;

    @JsonProperty(value="EndCharacter")
    private int endCharacter;

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getStartCharacter() {
        return startCharacter;
    }

    public void setStartCharacter(int startCharacter) {
        this.startCharacter = startCharacter;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public int getEndCharacter() {
        return endCharacter;
    }

    public void setEndCharacter(int endCharacter) {
        this.endCharacter = endCharacter;
    }

    public Range() {
        super();
    }

    public Range(String file,
                 int startLine,
                 int startCharacter,
                 int endLine,
                 int endCharacter) {
        this(null, null, file, startLine, startCharacter, endLine, endCharacter);
    }

    public Range(String repo,
                 String commit,
                 String file,
                 int startLine,
                 int startCharacter,
                 int endLine,
                 int endCharacter) {
        super(repo, commit);
        setFile(file);
        setStartLine(startLine);
        setStartCharacter(startCharacter);
        setEndLine(endLine);
        setEndCharacter(endCharacter);
    }

    @Override
    public int hashCode() {
        int ret = super.hashCode();
        ret = ret * 13 + this.getFile().hashCode();
        ret = ret * 13 + this.getStartLine();
        ret = ret * 13 + this.getStartCharacter();
        ret = ret * 13 + this.getEndLine();
        ret = ret * 13 + this.getEndCharacter();
        return ret;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof Range)) {
            return false;
        }
        Range that = (Range) o;
        return super.equals(o) &&
                StringUtils.equals(this.getFile(), that.getFile()) &&
                this.getStartLine() == that.getStartLine() &&
                this.getStartCharacter() == that.getStartCharacter() &&
                this.getEndLine() == that.getEndLine() &&
                this.getEndCharacter() == that.getEndCharacter();
    }


}
