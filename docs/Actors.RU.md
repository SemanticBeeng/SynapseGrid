Поддержка многопоточности
=========================

Система SynapseGrid интегрируется с превосходной библиотекой Akka. (Пользуясь случаем, хочу выразить благодарность
Jonas Bonér'у за создание этой библиотеки и Martin Odersky за создание языка Scala.)

Преимущества интеграции с Akka с точки зрения системы SynapseGrid
-----------------------------------------------------------------

1. Параллельная обработка данных.
2. Явное указание на то, какие подсистемы будут выполняться в отдельном экторе.
3. Возможность развёртывания сложной системы на нескольких узлах.

Преимущества SynapseGrid с точки зрения системы Akka Actor'ов
-------------------------------------------------------------

1. Наблюдение структуры системы посредством Dot-файлов.
2. Многовходовые экторы.
3. Типизированные входы и выходы экторов.
4. Наличие внешних выходов экторов. Обычные экторы не имеют явных наблюдаемых и управляемых выходов. 
Результат их работы может быть только побочным действием и скрытой отправкой сообщения чёрте-кому.
5. Внешнее связывание экторов. Обычные экторы предоставлены сами себе. Akka практически не предоставляет 
средств высокоуровневой композиции систем.

Планируется
-----------
1. Стратегия обработки исключений,
2. Пул экторов/подсистем.

Использование Akka Actor'ов
---------------------------

Описанные системы работают в одном потоке. Один из возможных способов перехода к многопоточности заключается в том,
чтобы превратить целую систему в Actor, который уже будет совместим с параллельностью Akka.

Если на вход Actor'а поступает Signal, то его обработка выполняется очевидным образом — сам сигнал передаётся 
во вложенную DynamicSystem. Для совместимости с программами, не работающими с сигналами, используется специальный 
контакт NonSignalWithSenderInput. Этот контакт имеет тип (ActorRef, Any). Первый элемент пары будет содержать sender 
поступивших данных, а второй элемент — собственно данные.

DSL для описания систем, содержащих экторы
------------------------------------------

Системы описываются совершенно также, как обычно. Для добавления подсистемы, которая будет представлена отдельным эктором,
используется команда addActorSubsystem. (DSL импортируется одним import'ом - import ru.primetalk.synapse.akka._)
Также можно произвольного эктора встроить как подсистему. Для этого используется childActorAdapterSnippet.

Системы, находящиеся внутри эктора, могут воспользоваться дополнительным DSL для взаимодействия с Akka. DSL преимущественно 
объявлен внутри расширения     

    val akkaExt = implicitly[AkkaSystemBuilderExtension] //sb.extend(akkaExtensionId)
    
Доступны контакты

    akkaExt.ContextInput
    akkaExt.SenderInput
    akkaExt.SelfInput
    
И состояния/переменные (в которых хранятся значения, соответствующие текущему обрабатываемому сообщению)

    akkaExt.self
    akkaExt.sender
    akkaExt.context
    
От имени текущего эктора можно отправить сообщение 

    someContact.from(self).tellToActor(actorRef)
    someContact.tellToActorFromSelf(actorRef)

Если адрес эктора хранится в переменной состояния, то можно напрямую отправить сообщение этому эктору:

    someContact.toActorIndirect(state)