<?php
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);

require_once("rest/dao/TodoDao.class.php");

$description = $_REQUEST['description'];
$created = $_REQUEST['created'];

$dao = new TodoDao();
$results = $dao->add($description, $created);
print_r($results);
?>
