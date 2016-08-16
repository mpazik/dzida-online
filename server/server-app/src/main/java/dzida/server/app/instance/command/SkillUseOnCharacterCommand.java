package dzida.server.app.instance.command;

import dzida.server.core.basic.entity.Id;
import dzida.server.core.character.model.Character;
import dzida.server.core.skill.Skill;

public class SkillUseOnCharacterCommand implements InstanceCommand {
    public final Id<Skill> skillId;
    public final Id<Character> target;
    public final Id<Character> characterId;

    public SkillUseOnCharacterCommand(Id<Character> characterId, Id<Skill> skillId, Id<Character> target) {
        this.skillId = skillId;
        this.target = target;
        this.characterId = characterId;
    }
}