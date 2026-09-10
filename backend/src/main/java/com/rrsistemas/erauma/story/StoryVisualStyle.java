package com.rrsistemas.erauma.story;

import org.springframework.stereotype.Component;

@Component
public class StoryVisualStyle {
    public String cartoonPrompt() {
        return """
                Ilustração cartoon infantil de alta qualidade, alegre, acolhedora e expressiva, com traços suaves, formas arredondadas, personagens carismáticos, expressões faciais claras, cores vivas e harmoniosas, iluminação suave e delicada, proporções adequadas à idade, cenário detalhado sem excesso de elementos, composição limpa, boa separação entre personagens e cenário e acabamento visual consistente para leitura em tela de celular.
                Sem realismo fotográfico, terror, aparência sombria, texto, letras, números, símbolos, balões, legendas, título, assinatura, logotipo ou marca-d'água. Não imite nem mencione artistas, estúdios, filmes, séries, personagens, franquias ou estilos protegidos.
                """.trim();
    }
}
