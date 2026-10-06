-- CasaCapital — dia da semana da tarefa para o quadro kanban (1 = segunda … 7 = domingo; NULL = usa o dia do prazo)

ALTER TABLE tarefa ADD COLUMN dia_semana INT NULL AFTER prazo;
