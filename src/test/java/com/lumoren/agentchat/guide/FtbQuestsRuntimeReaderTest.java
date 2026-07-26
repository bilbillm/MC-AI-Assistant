package com.lumoren.agentchat.guide;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FtbQuestsRuntimeReaderTest {
    @Test
    void readFromQuestFile_extractsQuestProgressAndText() {
        FakeQuest incomplete = new FakeQuest(2, "Basic Generator",
                "Power start", List.of("Craft the generator before wiring FE"), List.of(new FakeTask(20, "Submit Iron")));
        FakeQuest completed = new FakeQuest(3, "Wood Age",
                "", List.of("Punch a tree"), List.of());
        FakeChapter chapter = new FakeChapter(10, "Getting Started", List.of(incomplete, completed));
        FakeQuestFile questFile = new FakeQuestFile(List.of(new FakeGroup(List.of(chapter))));
        FakeTeamData teamData = new FakeTeamData(completed.id);
        FtbQuestsRuntimeReader reader = new FtbQuestsRuntimeReader();

        List<GuideEvidence> evidence = reader.readFromQuestFile(questFile, teamData,
                GuideIntent.WHAT_NEXT, "generator FE");

        assertFalse(evidence.isEmpty());
        assertEquals(GuideEvidence.SourceType.FTB_QUESTS, evidence.get(0).sourceType());
        assertTrue(evidence.get(0).summary().contains("1/2"));
        assertTrue(evidence.stream().anyMatch(e -> e.title().contains("Basic Generator")));
        assertTrue(evidence.stream().anyMatch(e -> e.summary().contains("Craft the generator")));
        assertTrue(evidence.stream().anyMatch(e -> e.summary().contains("Submit Iron")));
    }

    public static final class FakeQuestFile {
        private final List<FakeGroup> groups;

        FakeQuestFile(List<FakeGroup> groups) {
            this.groups = groups;
        }

        public List<FakeGroup> getChapterGroups() {
            return groups;
        }
    }

    public static final class FakeGroup {
        private final List<FakeChapter> chapters;

        FakeGroup(List<FakeChapter> chapters) {
            this.chapters = chapters;
        }

        public List<FakeChapter> getChapters() {
            return chapters;
        }
    }

    public static final class FakeChapter {
        private final long id;
        private final String title;
        private final List<FakeQuest> quests;

        FakeChapter(long id, String title, List<FakeQuest> quests) {
            this.id = id;
            this.title = title;
            this.quests = quests;
        }

        public long getId() {
            return id;
        }

        public String getRawTitle() {
            return title;
        }

        public List<FakeQuest> getQuests() {
            return quests;
        }
    }

    public static final class FakeQuest {
        private final long id;
        private final String title;
        private final String subtitle;
        private final List<String> description;
        private final List<FakeTask> tasks;

        FakeQuest(long id, String title, String subtitle, List<String> description, List<FakeTask> tasks) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.description = description;
            this.tasks = tasks;
        }

        public long getId() {
            return id;
        }

        public String getRawTitle() {
            return title;
        }

        public String getRawSubtitle() {
            return subtitle;
        }

        public List<String> getRawDescription() {
            return description;
        }

        public List<FakeTask> getTasks() {
            return tasks;
        }

        public boolean isVisible(FakeTeamData teamData) {
            return true;
        }
    }

    public static final class FakeTask {
        private final long id;
        private final String title;

        FakeTask(long id, String title) {
            this.id = id;
            this.title = title;
        }

        public long getId() {
            return id;
        }

        public String getRawTitle() {
            return title;
        }
    }

    public static final class FakeTeamData {
        private final long completedId;

        FakeTeamData(long completedId) {
            this.completedId = completedId;
        }

        public boolean isCompleted(FakeQuest quest) {
            return quest.id == completedId;
        }

        public boolean isStarted(FakeQuest quest) {
            return quest.id != completedId;
        }

        public int getRelativeProgress(FakeQuest quest) {
            return quest.id == completedId ? 100 : 35;
        }

        public int getRelativeProgress(FakeChapter chapter) {
            return 50;
        }

        public long getProgress(FakeTask task) {
            return task.id == 20 ? 4 : 0;
        }
    }
}
