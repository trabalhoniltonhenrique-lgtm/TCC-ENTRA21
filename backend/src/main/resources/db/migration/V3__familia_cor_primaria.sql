-- CasaCapital — cor principal do layout escolhida pela família (NULL = azul padrão)

ALTER TABLE familia ADD COLUMN cor_primaria VARCHAR(7) NULL AFTER agrupar_cat;
