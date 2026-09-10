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

    public String brazilianPortugueseGuidance() {
        return """
                Portugues brasileiro obrigatorio:
                - escreva em portugues brasileiro natural; nao misture portugues europeu, ingles ou espanhol, salvo nome proprio ou termo estrangeiro indispensavel ao tema;
                - garanta concordancia verbal e nominal, conjugacao, regencia, pronomes, genero, singular e plural, pontuacao e acentuacao corretos;
                - mantenha o mesmo genero, idade, nome, relacao familiar e referencia pronominal de cada personagem em toda a historia;
                - mantenha tempos verbais coerentes e transicoes naturais entre frases, paragrafos e blocos;
                - elimine frases incompletas, palavras ausentes, palavras duplicadas por acidente e construcoes que parecam traducao literal;
                - apresente cada personagem de modo natural uma unica vez e, depois, use seu nome, pronome ou relacao sem reapresentacoes repetitivas;
                - prefira construcoes como "Fernando e sua amiga Ana", "Fernando estava acompanhado de sua amiga Ana" ou "Ao lado dele estava Ana, sua melhor amiga";
                - nunca escreva construcoes artificiais como "a amiga chama Ana chegou", "Fernando e Ana estava" ou "as criancas correu";
                - trate Papai, Mamãe, Vovó e Vovô como nomes afetivos ou relações familiares: use "Papai", "Mamãe", "seu pai", "sua mãe", "sua avó" ou "seu avô", conforme o contexto; nunca "o homem chamado Papai" ou "a personagem chamada Mamãe".

                Antes de responder, faca silenciosamente uma revisao editorial completa. Confira frase por frase a gramatica, as referencias de genero e pronomes, os tempos verbais, nomes proprios, naturalidade, continuidade e desfecho. Corrija internamente qualquer problema e devolva apenas o JSON final, sem comentarios sobre a revisao.
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
