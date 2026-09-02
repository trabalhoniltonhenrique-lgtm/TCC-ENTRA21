-- CasaCapital — bootstrap do banco de dados
--
-- Rode este script UMA VEZ como root (ou outro usuário administrador), via
-- MySQL Workbench ou `mysql -u root -p < backend/db/bootstrap.sql`.
--
-- Troque 'CHANGE_ME' por uma senha sua antes de rodar, e use a MESMA senha
-- na variável de ambiente DB_PASSWORD ao rodar o backend (veja README/instruções
-- de setup). Nunca comite a senha real em nenhum arquivo do repositório.

CREATE DATABASE IF NOT EXISTS casacapital
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'casacapital_app'@'localhost' IDENTIFIED BY 'CasaCapital_2026!';

GRANT ALL PRIVILEGES ON casacapital.* TO 'casacapital_app'@'localhost';

FLUSH PRIVILEGES;
