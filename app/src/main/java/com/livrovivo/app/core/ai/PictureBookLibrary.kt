package com.livrovivo.app.core.ai

import com.livrovivo.app.domain.model.*

/** Short authored books. Existing books retain their original engine and plot. */
object PictureBookLibrary {
    private data class Book(
        val title: String,
        val openings: List<String>,
        val scenes: List<String>,
        val choices: List<List<String>>,
        val outcomes: List<List<String>>,
        val mood: String = "alegre"
    )

    fun recognizes(story: Story): Boolean = story.chapters.firstOrNull()?.sceneImagePrompt?.startsWith("Edition v2:") == true

    fun opening(brief: StoryBrief, themeId: String?): OfflineStoryEngine.Opening {
        val scene = ThemeOption.sceneFor(themeId, brief.theme)
        val book = books.getValue(scene)
        val variant = Math.floorMod(brief.editionSeed.hashCode(), book.openings.size)
        return OfflineStoryEngine.Opening(fill(book.title, brief), Chapter(
            index = 1,
            content = fill(book.openings[variant], brief),
            choices = choices(book, 0), mood = book.mood,
            sceneImagePrompt = "Edition v2: ${scene.name}:$variant. Illustrate this specific opening: ${fill(book.openings[variant], brief)}"
        ), 4)
    }

    fun continuation(brief: StoryBrief, story: Story, choice: Choice): Chapter {
        val sceneName = story.chapters.first().sceneImagePrompt!!.substringAfter("Edition v2: ").substringBefore(":")
        val book = books.getValue(SceneKind.valueOf(sceneName))
        val index = story.lastChapter!!.index + 1
        val stage = (index - 2).coerceIn(0, 2)
        val picked = story.lastChapter!!.choices.indexOfFirst { it.text == choice.text }.coerceIn(0, 1)
        val content = fill(book.outcomes[stage][picked] + "\n\n" + book.scenes[stage], brief)
        return Chapter(index = index, content = content,
            choices = if (index >= 4) emptyList() else choices(book, index - 1),
            isEnding = index >= 4, mood = book.mood,
            sceneImagePrompt = "Published picture book scene, $sceneName. Depict this moment: $content")
    }

    private fun choices(book: Book, stage: Int) = book.choices[stage].mapIndexed { i, text ->
        Choice(text, stage + 2, if (i == 0) Virtue.CURIOSIDADE else Virtue.CRIATIVIDADE)
    }
    private fun fill(text: String, brief: StoryBrief): String = text
        .replace("{n}", StoryPrompts.sanitizeInput(brief.child.name, 40))
        .replace("{c}", brief.companion.name)

    private val books = mapOf(
        SceneKind.NIGHT to Book(
            "O Coelho que Cresceu na Parede",
            listOf(
                "Havia um coelho enorme na parede de {n}. Enorme mesmo!\n\n{c} olhou atrás da cortina. Nada. Olhou debaixo da cama. Uma meia.\n\nO coelho mexeu a orelha. A meia, não.\n\n— Acho que ele quer brincar — sussurrou {n}.",
                "— Sua orelha está no teto! — disse {n}.\n\n{c} apalpou a própria cabeça. As orelhas estavam no lugar. Mas, na parede, um coelho comprido balançava.\n\nA luminária iluminava um brinquedo na prateleira. O que aconteceria se a luz mudasse de lugar?",
                "{n} apagou a luz grande. Foi quando apareceu: um coelho do tamanho do armário!\n\n{c} escondeu o nariz no cobertor, deixando o resto de fora.\n\n— Um esconderijo muito pequeno — riu {n}.\n\nA luminária continuava acesa. E se o coelho fosse só uma sombra?"
            ),
            listOf("Na prateleira havia um coelhinho de pano. Na parede, um gigante!\n\n{n} aproximou a mão da luz. Cinco dedos viraram cinco árvores. {c} abriu a boca de espanto.", "A parede virou um palco. Faltava uma última apresentação antes de dormir.\n\n— Só não vale fazer barulho de elefante — pediu {c}, já bocejando.", "{n} ajeitou o coelho de pano no travesseiro. A sombra descansou na parede.\n\n— Boa noite, coelho grande. Boa noite, coelho pequeno.\n\nDois coelhos. Um só soninho."),
            listOf(listOf("Mover a luminária", "Examinar o coelhinho"), listOf("Fazer uma borboleta", "Inventar um caracol"), listOf("Apresentar uma dança silenciosa", "Contar uma história baixinho")),
            listOf(listOf("{n} moveu a luminária. O coelho encolheu! {c} espiou por cima do cobertor.", "{n} examinou o coelhinho. Levantou uma orelha de pano. A orelha na parede também subiu!"), listOf("{n} cruzou os polegares. Duas asas dançaram na parede. {c} tentou pousar a borboleta no próprio nariz.", "{n} fechou a mão e esticou dois dedos. Um caracol passeou devagar. {c} levou um bocejo inteiro para acompanhá-lo."), listOf("{n} fez as sombras dançarem sem nenhum som. Até o cobertor pareceu ficar mais quietinho.", "{n} contou que a sombra também precisava de cama. {c} apontou o cantinho macio da parede.")), "sonolento"
        ),
        SceneKind.SCHOOL to Book(
            "A Ponte de Um Bloco Só",
            listOf("PLOC! Um bloco vermelho rolou até o sapato de {n}.\n\nNa sala nova, alguém tentava construir uma ponte. Ela caía sempre no meio.\n\n{c} cochichou: — Acho que esse bloco está procurando trabalho.\n\n{n} ainda não sabia o nome de ninguém.", "A cadeira de {n} tinha um desenho de sol. A mesa tinha uma ponte quebrada.\n\n— Ela não fica em pé — disse uma criança de camiseta listrada.\n\n{c} trouxe um bloco na cabeça.\n\n— Entrega especial!", "{n} ensaiou um oi bem baixinho. Tão baixinho que só {c} ouviu.\n\nCRAC! Uma ponte de blocos caiu na mesa ao lado.\n\nUma criança suspirou. {n} tinha um bloco vermelho na mão. Talvez fosse mais fácil começar por ele."),
            listOf("— Sou Dani — disse a criança. — A ponte precisa atravessar esse rio azul.\n\nO rio era uma fita no chão. {n} e Dani tinham muitos blocos pequenos e uma peça comprida.", "A ponte ficou de pé. Mas o ônibus de brinquedo era largo demais!\n\n{c} tentou passar de lado. Ficou com o nariz preso entre dois blocos. Todos riram.", "O ônibus atravessou levando três bonecos. Dani guardou um lugar ao lado.\n\n— Amanhã fazemos uma estação?\n\n{n} pendurou a mochila perto da cadeira de sol. Agora aquele lugar tinha um nome: aqui."),
            listOf(listOf("Oferecer o bloco vermelho", "Perguntar sobre a ponte"), listOf("Construir dois apoios", "Testar a peça comprida"), listOf("Alargar a ponte juntos", "Fazer uma passagem ao lado")),
            listOf(listOf("{n} ofereceu o bloco vermelho. Dani abriu espaço na mesa: — Vem construir comigo!", "{n} perguntou onde a ponte precisava chegar. Dani desenhou o caminho com o dedo e abriu espaço na mesa."), listOf("{n} empilhou blocos nas duas margens. Dani colocou a travessa por cima. Dessa vez ela não caiu!", "{n} testou a peça comprida enquanto Dani segurava as pontas. Ela alcançou a outra margem certinho!"), listOf("{n} segurou uma ponta e Dani afastou a outra. A ponte ganhou espaço para o ônibus.", "{n} fez uma passagem larga ao lado. Agora havia uma ponte para pedestres e outra para veículos."))
        ),
        SceneKind.SPACE to Book(
            "Saturno Perdeu Uma Meia",
            listOf("Uma meia vermelha bateu na janela do foguete. TOC!\n\n{n} olhou para {c}. {c} olhou para os próprios pés.\n\n— Não é minha.\n\nDo lado de fora, um robozinho girava devagar. Em seu varal espacial faltava uma peça.", "— Atenção, objeto voador muito... fofinho! — anunciou {c}.\n\n{n} espiou a janela do foguete. Uma meia passeava entre as estrelas. Atrás dela vinha um robô com um cesto vazio.\n\nO robô fez sinal pela janela.", "O radar de {n} mostrou um pontinho vermelho. Não era planeta. Não era cometa.\n\nEra uma meia!\n\n{c} apertou o botão do rádio. Um robozinho respondeu: — Meu varal soltou!\n\nA meia flutuava perto da nave."),
            listOf("O robô se chamava Pingo. Suas roupas escaparam quando uma mola do varal fez PÓIM!\n\n{n} viu três lenços rodando perto de um pequeno satélite. A nave tinha uma rede macia e um braço mecânico.", "As roupas voltaram ao cesto, mas a mola continuava solta.\n\nPingo tentou prender um lenço. PÓIM! O lenço pousou na cabeça de {c}.\n\n— Um chapéu espacial!", "Pingo pendurou tudo outra vez. A meia vermelha ficou bem no meio.\n\n{n} acenou pela janela. Ao longe, o varal parecia uma bandeirinha colorida.\n\nO radar fez PIM. {c} conferiu os pés, só por garantia."),
            listOf(listOf("Chamar o robô pelo rádio", "Acender o sinal da nave"), listOf("Abrir a rede macia", "Usar o braço mecânico"), listOf("Prender a mola com fita", "Inventar pregadores novos")),
            listOf(listOf("{n} chamou pelo rádio. O robô parou de girar e se aproximou da janela.", "{n} acendeu o sinal. O robô viu o pisca-pisca e guiou seu pequeno veículo até a nave."), listOf("{n} abriu a rede. Os três lenços pousaram dentro como borboletas cansadas.", "{n} moveu o braço mecânico bem devagar. Ele pegou os lenços, um por um, sem amassar nenhum."), listOf("{n} mandou a fita pelo braço mecânico. Pingo enrolou a mola até ela parar de saltar.", "{n} desenhou pregadores largos na tela. Pingo os montou com peças do cesto. Nenhum lenço escapou.")), "aventura"
        ),
        SceneKind.PARTY to Book(
            "O Trem que Não Cabia na Festa",
            listOf("O urso trouxe um bolo. A boneca trouxe uma xícara. {n} trouxe um trem inteiro.\n\nMas o trem não passava pela porta da casinha!\n\n{c} tentou encolher a barriga. O trem não tinha barriga para encolher.", "— Falta um convidado! — disse {n}.\n\nTodos os brinquedos estavam na festa, menos o trem. Sua locomotiva tinha parado na porta da casinha.\n\n{c} mediu a porta com os braços. Depois mediu o trem.\n\n— Temos um probleminha comprido.", "PIUÍ! O trem de {n} chegou à festa. PIUÍ! A porta era pequena. PIUÍ...\n\n— Ele só sabe dizer isso? — perguntou {c}.\n\nO urso esperava com um pratinho de bolo. Ninguém queria deixar o trem de fora."),
            listOf("Na sala havia lugar, mas o trilho terminava longe da mesa. {n} encontrou uma caixa de blocos.\n\nO urso quis fazer uma estação. A boneca quis uma ponte. Só havia blocos para uma coisa de cada vez.", "A construção ficou pronta. O trem chegou!\n\nAgora faltava levar os pratos até os convidados. {c} empilhou três na cabeça. TILIM! Melhor inventar outro jeito.", "O último vagão parou junto do urso. Havia um pratinho para cada um.\n\n{n} sentou ao lado de {c}. A festa já não cabia na casinha.\n\nAinda bem: na sala cabia todo mundo."),
            listOf(listOf("Levar a festa para fora", "Abrir a casinha de brinquedo"), listOf("Construir a estação primeiro", "Começar pela ponte"), listOf("Transportar pratos nos vagões", "Organizar uma fila de ajudantes")),
            listOf(listOf("{n} estendeu um tapete na sala. Os convidados levaram as xícaras para perto do trem.", "{n} abriu as duas metades da casinha. A parede virou um quintal enorme para receber o trem."), listOf("{n} montou a estação com o urso. Depois, a boneca usou a caixa vazia como ponte.", "{n} montou a ponte com a boneca. O urso desenhou uma estação num pedaço de papel."), listOf("{n} colocou um prato em cada vagão. O trem virou um garçom comprido e muito cuidadoso.", "{n} entregou um prato ao urso, que passou para a boneca. {c} ajudou com os guardanapos."))
        ),
        SceneKind.HOME to Book(
            "Um Colo com Lugar para Dois",
            listOf("A meia do bebê cabia na mão de {n}. A de {n} quase cobria o pé de {c}.\n\n— Uma cabana! — disse {c}, entrando nela.\n\nO bebê chorou. Mamãe foi até o berço. {n} ficou segurando a meia pequena.", "{n} tinha feito uma torre enorme. Mas todo mundo estava olhando o bebê.\n\n{c} espiou pela torre: — Posso morar aqui?\n\n{n} não respondeu. Queria mostrar a construção. E queria um colo também.", "O bebê espirrou: atchim! Todo mundo sorriu.\n\n{n} espirrou de brincadeira: ATCHIM! {c} caiu de costas, rindo.\n\nMas {n} queria mais que risadas. Queria um tempo bem pertinho de mamãe."),
            listOf("Mamãe sentou no tapete com {n}.\n\n— Você continua tendo lugar aqui — disse, abrindo um braço.\n\nO bebê mexeu os pés. {c} fez uma careta. Será que aquele pequenino sabia brincar?", "O bebê olhou, olhou... e soltou um barulhinho: agu!\n\n{n} respondeu: agu! {c} respondeu: abacaxi!\n\nA conversa ficou muito engraçada. Depois, o bebê fechou os olhos.", "Mamãe leu a página favorita de {n}. {c} ficou ouvindo no tapete.\n\nPerto do berço havia duas meias: uma grande, outra pequenina.\n\nNão eram do mesmo tamanho. As duas tinham lugar na gaveta."),
            listOf(listOf("Pedir um abraço", "Mostrar como estou sentindo"), listOf("Fazer uma careta engraçada", "Cantar uma canção curtinha"), listOf("Escolher um livro com mamãe", "Pedir um momento só nosso")),
            listOf(listOf("{n} pediu um abraço. Mamãe chamou: — Vem pertinho. O bebê já está seguro no berço.", "{n} mostrou a carinha triste. Mamãe chegou perto e escutou sem apressar nenhuma palavra."), listOf("{n} fez uma careta. O bebê acompanhou o movimento com os olhos, muito atento.", "{n} cantou devagar. Os pezinhos do bebê mexeram no ritmo, como dois pequenos músicos."), listOf("{n} escolheu um livro e se ajeitou ao lado de mamãe. Era a vez daquela história.", "{n} pediu um momento só dos dois. Mamãe trouxe o livro favorito e chegou bem pertinho.")), "aconchegante"
        ),
        SceneKind.OCEAN to Book(
            "O Concerto das Bolhas",
            listOf("BLUP. BLUP. BLUP!\n\nO polvo regia um concerto, mas só saíam bolhas. {n} e {c} ouviam pela janela de um submarino.\n\n— Sumiu o sino de concha! — disse o polvo pelo rádio.\n\nSem ele, ninguém sabia quando começar.", "A baleia abriu a boca para cantar. O polvo levantou os oito braços.\n\nSilêncio.\n\n{n} bateu no rádio do submarino. Não estava quebrado. O concerto é que esperava um sino perdido.\n\n{c} apontou marcas na areia.", "Havia um convite grudado na janela do submarino de {n}: concerto submarino.\n\nMas, ao chegar, todos estavam procurando alguma coisa. Até {c} procurou debaixo do banco.\n\n— Uma concha que faz DONG! — explicou o polvo."),
            listOf("Um caranguejo dormia abraçado à concha. Para ele, era um travesseiro perfeito!\n\n{n} falou pelo rádio. O caranguejo abriu um olho. Não queria perder a soneca.", "O sino voltou, mas ficou cheio de areia. DONG virou PUF.\n\nO polvo tentou de novo. PUF! {c} riu tanto que embaçou a janela do submarino.", "DONG! A baleia cantou grave. Os camarões marcaram o ritmo. O polvo regeu com os oito braços.\n\n{n} escutou sem dizer nada.\n\nBem baixinho, entre as notas, o caranguejo roncava: blup... blup..."),
            listOf(listOf("Seguir as marcas na areia", "Perguntar aos peixes"), listOf("Oferecer uma esponja macia", "Esperar a soneca acabar"), listOf("Pedir ajuda à correnteza", "Virar a concha devagar")),
            listOf(listOf("{n} seguiu as marquinhas com o submarino. Elas terminavam atrás de uma pedra.", "{n} perguntou aos peixes pelo rádio. Um cardume apontou, todo junto, para uma pedra."), listOf("{n} pediu ao polvo uma esponja. O caranguejo testou o travesseiro novo e soltou a concha.", "{n} esperou ouvindo o mar. Quando o caranguejo acordou, entregou a concha e pediu um lugar no concerto."), listOf("{n} apontou a correnteza suave. O polvo segurou o sino ali, e a água levou a areia.", "{n} mostrou como virar a concha. O polvo virou bem devagar. A areia caiu como uma cascata pequenina.")), "misterioso"
        ),
        SceneKind.DINOSAURS to Book(
            "O Ovo que Fazia Toc-Toc",
            listOf("TOC-TOC.\n\n{n} respondeu: toc-toc! {c} olhou para a árvore. O som não vinha de lá.\n\nNo ninho de folhas havia um ovo enorme. Uma dinossaura de pescoço comprido esperava ao lado.\n\n— Acho que alguém quer conversar — disse {n}.", "O ovo balançou. {c} balançou junto. O ovo parou. {c} quase caiu.\n\n{n} ouviu um toc-toc bem baixinho. A mamãe dinossaura ajeitou as folhas do ninho.\n\nEstava chegando alguém novo.", "{n} encontrou pegadas maiores que uma almofada. Todas rodeavam um ninho.\n\nDentro dele, um ovo fazia TOC. Depois TOC-TOC.\n\n{c} tentou contar nos dedos. A mamãe dinossaura sorriu com os olhos."),
            listOf("CRIC! Apareceu uma rachadura. Depois um focinho. Depois... um espirro!\n\nO filhote saiu com uma folha grudada na cabeça. Queria alcançar mamãe, mas seus pés escorregavam nas folhas lisas.", "O filhote chegou até mamãe. Então ouviu um PLOC no lago e levantou as orelhas.\n\nEra uma rã. Tão pequena que quase sumia na folha. O filhote queria vê-la sem assustar.", "A rã pulou. O filhote tentou pular também: POF! Sentou no chão.\n\n{n} riu com ele. Mamãe encostou o focinho em sua cabeça.\n\nNo ninho ficou a casca vazia. No mundo, uma pegada nova."),
            listOf(listOf("Responder com duas batidinhas", "Escutar bem pertinho da mamãe"), listOf("Arrumar um caminho de folhas", "Chamar mamãe para mais perto"), listOf("Observar a rã em silêncio", "Imitar um coaxo baixinho")),
            listOf(listOf("{n} bateu duas vezes numa pedra, longe do ovo. De dentro veio a resposta: toc-toc!", "{n} ficou ao lado da mamãe e escutou. O ovo respondeu com um toc-toc apressado."), listOf("{n} virou as folhas ásperas para cima. O filhote apoiou os pés e deu seu primeiro passo.", "{n} chamou a mamãe. Ela se aproximou, oferecendo o focinho para o filhote se apoiar."), listOf("{n} ficou quietinho com {c}. O filhote imitou. A rã saiu para a beirinha da folha.", "{n} fez um coaxo baixinho. A rã respondeu. O filhote tentou, mas só saiu um piado!")), "aventura"
        ),
        SceneKind.FOREST to Book(
            "Quem Guardou o Outono?",
            listOf("Uma bolota caiu no chapéu de {c}. Depois outra. Depois mais três!\n\n{n} olhou para cima. Um esquilo tentava guardar comida num buraco pequeno demais.\n\n— Minha despensa encolheu! — reclamou ele.", "O esquilo tinha uma lista, sete bolotas e nenhum lugar para pôr a oitava.\n\n{n} encontrou a oitava dentro do sapato de {c}.\n\n— Péssima despensa — disse {c}, sacudindo o pé.\n\nPrecisavam de uma ideia melhor.", "— Procura-se um armário! — gritou alguém entre as folhas.\n\n{n} parou para ouvir. Era um esquilo equilibrando bolotas até em cima das orelhas.\n\n{c} tentou ajudar. Uma bolota rolou até o riacho."),
            listOf("O lugar novo era espaçoso, mas ficava depois do riacho. A ponte tinha uma tábua solta.\n\n{n} viu galhos firmes na margem. O esquilo tinha um cesto e pressa demais.", "As bolotas chegaram do outro lado. O esquilo as guardou tão depressa que esqueceu onde ficava a entrada!\n\n{c} apontou três árvores quase iguais.\n\n— Essa? Aquela? Ou... essa de novo?", "O esquilo encontrou a despensa e convidou todo mundo para um lanche.\n\n{n} guardou uma bolota no chão e cobriu com terra.\n\n— Essa fica para depois.\n\nBem depois, seria uma árvore."),
            listOf(listOf("Procurar um tronco oco", "Pedir uma ideia ao esquilo"), listOf("Consertar a ponte juntos", "Passar o cesto pelo galho"), listOf("Desenhar um mapa de folhas", "Marcar a entrada com pedrinhas")),
            listOf(listOf("{n} encontrou um tronco oco e seco. O esquilo entrou, deu uma volta e aprovou o tamanho.", "{n} perguntou onde o esquilo gostaria de morar. Ele mostrou um tronco seco na outra margem."), listOf("{n} segurou os galhos enquanto {c} encaixava a tábua. O esquilo atravessou com passos pequenos.", "{n} e {c} deslizaram o cesto por um galho baixo sobre o riacho. O esquilo recebeu na outra margem."), listOf("{n} desenhou o riacho e as árvores numa folha grande. O esquilo seguiu o desenho até a porta.", "{n} fez uma seta de pedrinhas. O esquilo correu até a entrada e voltou para conferir a seta.")), "misterioso"
        )
    )
}
