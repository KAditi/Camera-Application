<?php
	$name = $_POST["Name"];
	$image = $_POST["Image"];
	
	$decodedImage = base64_decode("$image");
	file_put_contents("uploads/".$name.".JPG",$decodedImage)
?>