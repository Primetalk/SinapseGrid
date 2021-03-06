Потребности систем ведения диалога
==================================

Для интеграции компонентов системы в Речевом портале и продуктах на его основе применяется Система Контактов.

Рассмотрим особенности функционирования систем реального времени и преимущества Системы Контактов.

Потребности систем реального времени
------------------------------------

Речевой портал является основой для разработки программных систем, которые должны работать в режиме реального времени. Такой режим работы характеризуется следующими свойствами:

    квантование и дискретизация непрерывных сигналов;
    порционная обработка данных, буферизация.

Первичная обработка непрерывных сигналов реального времени приводит к появлению дискретных событий, содержащих результаты обработки сигнала.

Квантование и дискретизация непрерывных сигналов
------------------------------------

При квантовании в каждый момент времени непрерывный сигнал с вещественными значениями округляется до ближайшего фиксированного уровня. При дискретизации значение сигнала записывается в дискретные моменты времени, а не непрерывно. Например, речевой сигнал квантуется до 16тыс. уровней и записывается с частотой дискретизации 16 кГц.

Буферизация (фреймы)
------------------------------------

Обрабатывать данные по одному отсчёту неэффективно — слишком большие накладные расходы на передачу отдельных отсчётов. Поэтому данные обрабатываются сразу большими порциями — фреймами. Для распознавания речи принято использовать фреймы размером 10 мс. Это связано с тем, что большинство звуков длится больше 10 мс и в пределах фрейма сигнал можно с определённой натяжкой считать стационарным. При частоте дискретизации 16 кГц за один раз обрабатывается 160 отсчётов.

Вторичная буферизация
------------------------------------

Для оценки медленно меняющихся параметров необходимо анализировать более длительные отрезки сигнала. Поэтому применяется вторичная буферизация. Например, для определения моментов начала и окончания речи необходимо анализировать сигнал в интервале сотен миллисекунд.

Потребности систем обработки речевого сигнала
------------------------------------

Речь представляет собой сложный сигнал, из которого может быть извлечена разнообразная информация:

*    моменты начала и окончания речи;
*    величина паузы после окончания речи;
*    громкость и отношение сигнал/шум речевого сигнала;
*    вектор признаков для модуля распознавания;
*    интонация и личностные особенности говорящего;
*    текст речи (в результате распознавания речи);
*    смысл фраз (в результате семантического и интонационного анализа);
*    намерение говорящего (в результате прагматического анализа);
*    и др.

Каждый вид извлекаемой информации становится доступным после проведения вычислений согласно определённому алгоритму обработки. Информация представляется в форме событий, которые появляются в момент завершения обработки. Алгоритмы обработки характеризуются

*    разветвлённостью обработки данных;
*    наличием обратных связей;
*    распределённостью состояния.

Разветвлённость обработки данных
------------------------------------

Многие виды извлекаемой информации вычисляются на основе промежуточных данных. При этом объём анализируемых данных (и длина буферизации) может отличаться в разных алгоритмах, и, следовательно, события генерируются в различные моменты времени. Поэтому алгоритмы обработки с одной стороны, имеют много общих элементов, а с другой стороны, содержат отличающиеся части, формирующие соответствующие события.

Система, вычисляющая сразу много видов информации, приобретает древообразный вид.

Дальнейшая обработка данных предполагает накопление и агрегирование разнородной информации, вычисляемой разными ветками системы. Тем самым, происходит слияние потоков обработки событий.

Для описания такого рода систем применяются DataFlow диаграммы.

Обратная связь
------------------------------------

Особым случаем обработки информации является наличие циклов на DataFlow диаграмме системы. Циклы соответствуют передаче информации в «обратную» сторону, то есть в алгоритм, генерирующий низкоуровневые события, передаётся информация, вычисленная на более поздних стадиях обработки. Например, если в ходе диалога удалось установить личностные особенности говорящего, акустическая модель может быть заменена на более подходящую. Или при переходе диалога к диктовке номера телефона языковая модель может быть специализирована. Или могут быть настроены веса языковой модели.

При классической иерархической декомпозиции систем (структурный подход, объектно-ориентированный подход) построение обратных связей существенно затруднено. Для передачи данных на 10 шагов назад необходимо в 10 промежуточных компонентах предусмотреть call-back-функции, принимающие данные обратной связи. В то же время, на DataFlow диаграмме такая передача изображается одной связью независимо от числа промежуточных компонентов.

Потребности сложных систем обработки событий
------------------------------------


Потребностями обработка входного речевого сигнала не исчерпываются все потребности системы ведения диалога. В состав модулей системы ведения диалога входят такие компоненты:

*    модули обмена аудио-данными с телефонным аппаратом собеседника;
*    модуль выделения речевого сигнала;
*    модуль распознавания речи;
*    анализатор динамики реплик сторон диалога (определение пауз, перебиваний);
*    модуль обработки телефонных событий (входящий звонок, перенаправление вызова, завершение разговора);
*    модуль синтеза речи;
*    модуль управления воспроизведением речи;
*    модуль многоканального плеера;
*    модуль ведения аудио и текстового журнала сессии;
*    и др.

Большинство этих модулей также описываются с помощью DataFlow диаграмм, содержащих обратные связи и разветвлённые алгоритмы. Более того, целые модули также могут быть связаны между собой обратными связями.

Наличие в составе сложной системы значительного количества модулей и подсистем приводит к необходимости их организации. Необходимо обеспечить изоляцию, инкапсуляцию и интеграцию (interconnection) модулей. Кроме того, так как многие алгоритмы обработки речевого сигнала требуют значительных вычислительных ресурсов, необходимо обеспечить возможность параллельного выполнения алгоритмов.

Ещё одним фактором, свойственным сложным системам, является живучесть при наличии ошибок. При возникновении ошибки в одном из вспомогательных модулей вся система должна иметь возможность продолжать функционирование (возможно, с временной потерей части функций). В то же время, при возникновении ошибок в критических модулях система должна завершить работу с освобождением всех ресурсов.

В ходе создания и эксплуатации сложных систем возникает необходимость их отладки, диагностики и внесения изменений.

Таким образом, в сложных системах обработки событий возникают следующие потребности:

*    организация модулей (изоляция, инкапсуляция, интеграция);
*    параллельность выполнения;
*    обработка ошибок.

Существующие аналоги и прототипы
------------------------------------

Так как разработка Речевого портала ведётся на платформе Java VM (преимущественно на языке Scala), то наибольший интерес представляют прототипы, имеющие версии для этой платформы.

Наиболее близким аналогом системе контактов является библиотека Akka (TypeSafe, http://akka.io/), основанная, в свою очередь, на идеях языка Erlang.
