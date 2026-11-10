-- Тесты GymDB: тренажёрный зал
-- Таблицы: trainers (тренеры), clients (клиенты), programs (программы тренировок)
-- У тренера есть клиенты (clients.trainer_id), у клиента есть программы тренировок (programs.client_id)
-- Запуск: run.bat tests.sql (в IntelliJ: класс Main с аргументом tests.sql)
-- Внимание: в начале таблицы удаляются и создаются заново

DROP TABLE IF EXISTS programs;
DROP TABLE IF EXISTS clients;
DROP TABLE IF EXISTS trainers;

-- 1. Создание таблиц со всеми пятью типами данных и связями по ключу
CREATE TABLE trainers (id INT PRIMARY KEY, name STRING, specialization STRING, rating DOUBLE, hired DATE, certified BOOLEAN);
CREATE TABLE clients (
    id INT PRIMARY KEY,
    trainer_id INT REFERENCES trainers(id),
    name STRING,
    weight DOUBLE,
    joined DATE,
    active BOOLEAN
);
CREATE TABLE programs (
    id INT PRIMARY KEY,
    client_id INT REFERENCES clients(id),
    title STRING,
    goal STRING,
    start_date DATE,
    weeks INT,
    price DOUBLE,
    finished BOOLEAN
);

-- 2. Вставка одной записи и вставка части столбцов (остальные будут NULL)
INSERT INTO trainers VALUES (1, 'Иван Петров', 'силовые', 4.8, '2021-09-01', TRUE);
INSERT INTO trainers (id, name) VALUES (4, 'Мария Иванова');

-- 3. Вставка нескольких записей сразу
INSERT INTO trainers VALUES (2, 'Анна Смирнова', 'йога', 4.9, '2022-03-15', TRUE), (3, 'Олег Козлов', 'кроссфит', 4.2, '2023-06-01', FALSE);
SELECT * FROM trainers;

-- 4. Ожидается ошибка: тренер с id = 1 уже есть
INSERT INTO trainers VALUES (1, 'Дубликат', 'бокс', 4.0, '2024-01-01', TRUE);

-- 5. Ожидается ошибка: повтор ключа внутри одного запроса, не вставится ни одна строка
INSERT INTO trainers VALUES (5, 'А', 'бокс', 4.0, '2024-01-01', TRUE), (5, 'Б', 'бокс', 4.0, '2024-01-01', TRUE);

-- 6. Ожидаются ошибки типов
INSERT INTO trainers VALUES (6, 'Тест', 'бокс', 'высокий', '2024-01-01', TRUE);
INSERT INTO trainers VALUES (6, 'Тест', 'бокс', 4.0, '2024-13-45', TRUE);

-- 7. Клиенты: у каждого клиента есть тренер
INSERT INTO clients VALUES (1, 1, 'Дмитрий Орлов', 92.5, '2024-01-10', TRUE), (2, 1, 'Елена Волкова', 61.0, '2024-02-05', TRUE), (3, 2, 'Пётр Соколов', 78.3, '2023-11-20', FALSE), (4, 3, 'Ольга Морозова', 70.4, '2024-04-01', TRUE);
SELECT * FROM clients;

-- 8. Программы тренировок: у каждой программы есть клиент
INSERT INTO programs VALUES (1, 1, 'Силовой старт', 'набор массы', '2024-01-15', 12, 15000.0, TRUE), (2, 1, 'Силовой 2', 'набор массы', '2024-04-15', 8, 12000.0, FALSE), (3, 2, 'Стройность', 'похудение', '2024-02-10', 10, 11000.0, FALSE), (4, 3, 'Гибкость', 'растяжка', '2023-12-01', 6, 7000.0, TRUE), (5, 4, 'Кроссфит база', 'выносливость', '2024-04-05', 4, 6000.0, FALSE);
SELECT * FROM programs;

-- 9. Ожидаются ошибки: тренера с id = 42 нет, клиента с id = 99 нет
INSERT INTO clients VALUES (5, 42, 'Никита Белов', 80.0, '2024-05-01', TRUE);
INSERT INTO programs VALUES (6, 99, 'Бокс', 'выносливость', '2024-05-01', 4, 5000.0, FALSE);

-- 10. SELECT с выбором столбцов и условием по каждому типу
SELECT name, rating FROM trainers WHERE rating >= 4.5;
SELECT name FROM clients WHERE trainer_id = 1;
SELECT name, weight FROM clients WHERE weight > 75;
SELECT title, goal FROM programs WHERE client_id = 1;
SELECT title, start_date FROM programs WHERE start_date >= '2024-03-01';
SELECT title, weeks, price FROM programs WHERE weeks < 8;
SELECT * FROM programs WHERE finished = FALSE;

-- 11. UPDATE по условию
UPDATE trainers SET specialization = 'пилатес', rating = 4.5 WHERE id = 4;
UPDATE clients SET trainer_id = 4 WHERE id = 3;
UPDATE programs SET finished = TRUE WHERE weeks <= 4;
SELECT * FROM trainers WHERE id = 4;
SELECT name, trainer_id FROM clients WHERE id = 3;
SELECT title, finished FROM programs;

-- 12. Ожидаются ошибки: первичный ключ менять нельзя, тренера с id = 42 нет
UPDATE clients SET id = 10 WHERE id = 1;
UPDATE clients SET trainer_id = 42 WHERE id = 1;

-- 13. Ожидаются ошибки: у тренера 1 есть клиенты, у клиента 1 есть программы
DELETE FROM trainers WHERE id = 1;
DELETE FROM clients WHERE name = 'Дмитрий Орлов';

-- 14. DELETE по условию
DELETE FROM programs WHERE client_id = 3;
DELETE FROM clients WHERE active = FALSE;
SELECT * FROM clients;

-- 15. Индекс: сравните способ поиска до и после CREATE INDEX
SELECT * FROM clients WHERE name = 'Ольга Морозова';
CREATE INDEX idx_clients_name ON clients (name);
SELECT * FROM clients WHERE name = 'Ольга Морозова';
CREATE INDEX idx_clients_trainer ON clients (trainer_id);
SELECT name FROM clients WHERE trainer_id = 1;
CREATE INDEX idx_programs_price ON programs (price);
SELECT title, price FROM programs WHERE price > 10000;

-- 16. Ожидается ошибка: индекс с таким именем уже есть
CREATE INDEX idx_clients_name ON trainers (name);

-- 17. Ожидаются ошибки: на trainers ссылается clients, на clients ссылается programs
DROP TABLE trainers;
DROP TABLE clients;

-- 18. Ошибки в запросах
SELEC * FROM clients;
SELECT * FROM clients WHERE weight LIKE 80;
SELECT salary FROM trainers;
SELECT * FROM halls;
