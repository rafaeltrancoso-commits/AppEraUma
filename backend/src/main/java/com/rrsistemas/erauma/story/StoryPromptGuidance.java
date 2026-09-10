package com.rrsistemas.erauma.story;

import org.springframework.stereotype.Component;

@Component
public class StoryPromptGuidance {
    public String oralLanguageGuidance() {
        return """
                Linguagem desejada:
                - simples, clara, natural, acolhedora, divertida e afetiva, sem linguagem de bebe;
                - poetica e delicada com moderacao, agradavel para leitura em voz alta;
                - use dialogos naturais, pausas, musicalidade, sons pronunciaveis e metaforas simples;
                - use paragrafos moderados, pontuacao com pausas naturais e numeros por extenso quando isso melhorar a fala;
                - nao use emojis, listas, Markdown, simbolos decorativos ou abreviacoes desnecessarias no conto;
                - evite excesso de repeticoes, diminutivos, adjetivos, explicacoes e palavras estrangeiras.
                """;
    }

    public String ageGuidance(Integer age) {
        if (age != null && age >= 3 && age <= 4) {
            return "Para 3-4 anos: frases bem curtas, uma ideia por frase, paragrafos pequenos, acontecimentos concretos, poucos personagens, sequencia linear, repeticao leve e dialogos frequentes.";
        }
        if (age != null && age >= 5 && age <= 7) {
            return "Para 5-7 anos: frases um pouco maiores, aventura um pouco mais elaborada, pequenos misterios e relacoes simples de causa e consequencia, ainda com vocabulario infantil e compreensivel.";
        }
        if (age != null && age >= 8) {
            return "Para 8 anos ou mais: permita maior complexidade narrativa, humor, dialogos, metaforas simples e camadas de significado, mantendo seguranca emocional.";
        }
        return "Se a idade nao estiver informada ou o personagem for adulto, use como referencia de leitura uma crianca de 5-7 anos, sem transformar o personagem adulto em crianca.";
    }
}
