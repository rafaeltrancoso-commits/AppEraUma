package com.rrsistemas.erauma.story;

import org.springframework.stereotype.Component;

@Component
public class StoryPromptGuidance {
    public String oralLanguageGuidance() {
        return """
                Linguagem desejada:
                - escreva para criancas de 3 a 7 anos ouvirem em voz alta;
                - use palavras simples, frases curtas e paragrafos pequenos;
                - prefira dialogos naturais e ritmo de historia para dormir;
                - evite linguagem adulta, formal, abstrata ou literaria demais;
                - prefira palavras concretas como olhou, viu, perguntou, entrou e continuou;
                - evite palavras formais quando uma palavra simples resolver;
                - evite excesso de adjetivos;
                - permita pequenas repeticoes gostosas de ouvir;
                - use sons e surpresas pequenas quando fizer sentido;
                - mantenha comeco, aventura e conclusao faceis de acompanhar;
                - nao use linguagem de bebe.
                """;
    }

    public String ageGuidance(Integer age) {
        if (age != null && age >= 3 && age <= 4) {
            return "Para 3-4 anos: frases bem curtas, uma ideia por frase, paragrafos pequenos, acontecimentos concretos, poucos personagens, sequencia linear, repeticao leve e dialogos frequentes.";
        }
        if (age != null && age >= 5 && age <= 7) {
            return "Para 5-7 anos: frases um pouco maiores, aventura um pouco mais elaborada, pequenos misterios e relacoes simples de causa e consequencia, ainda com vocabulario infantil e compreensivel.";
        }
        return "Se a idade nao estiver informada, escreva com complexidade segura para criancas pequenas, priorizando compreensao oral.";
    }
}
